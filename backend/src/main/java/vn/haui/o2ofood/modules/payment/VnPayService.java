package vn.haui.o2ofood.modules.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceRepository;
import vn.haui.o2ofood.modules.cycle.ReservationEntity;
import vn.haui.o2ofood.modules.cycle.ReservationRepository;
import vn.haui.o2ofood.modules.cycle.ReservationStatus;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventStatus;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderRepository;
import vn.haui.o2ofood.modules.ordering.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VnPayService {

    private final VnPaySigner vnPaySigner;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TimeProvider timeProvider;
    private final ObjectMapper objectMapper;

    public VnPayService(VnPaySigner vnPaySigner,
                        PaymentAttemptRepository paymentAttemptRepository,
                        PaymentEventRepository paymentEventRepository,
                        OrderRepository orderRepository,
                        ReservationRepository reservationRepository,
                        InventoryBalanceRepository inventoryBalanceRepository,
                        OutboxEventRepository outboxEventRepository,
                        TimeProvider timeProvider,
                        ObjectMapper objectMapper) {
        this.vnPaySigner = vnPaySigner;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.timeProvider = timeProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * UC-C03: Return URL chỉ dùng để hiển thị trạng thái chờ xác nhận thanh toán cho khách,
     * tuyệt đối không tự ý chuyển trạng thái đơn hàng hoặc trừ tồn kho tại đây.
     */
    @Transactional
    public ReturnUrlStatusDto handleReturnUrl(Map<String, String> params) {
        OffsetDateTime now = timeProvider.now();
        boolean signatureValid = vnPaySigner.verifySignature(params);
        String txnRef = params.get("vnp_TxnRef");

        if (txnRef != null) {
            paymentAttemptRepository.findByTxnRef(txnRef).ifPresent(attempt -> {
                savePaymentEvent(attempt.getId(), "RETURN_URL", params, signatureValid, now);
            });
        }

        if (!signatureValid) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Chữ ký VNPAY Return URL không hợp lệ");
        }

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));
        OrderEntity order = orderRepository.findById(attempt.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        return new ReturnUrlStatusDto(
                order.getId(),
                order.getOrderCode(),
                txnRef,
                order.getOrderStatus().name(),
                order.getPaymentStatus().name(),
                "Đang chờ xác nhận thanh toán chính thức (IPN) từ máy chủ VNPAY"
        );
    }

    /**
     * UC-C03, AT-05, AT-06: Xử lý Webhook IPN từ máy chủ VNPAY.
     * - Kiểm tra chữ ký vnp_SecureHash (HMAC-SHA512).
     * - Chống xử lý trùng lặp (Idempotent khi VNPAY gọi lại nhiều lần).
     * - Phát hiện thanh toán đến muộn sau khi hết hạn giữ tồn 30 phút hoặc sau giờ chốt 00:00 -> LATE_PAID_RECONCILIATION.
     */
    @Transactional
    public VnPayIpnResponse processIpnWebhook(Map<String, String> params) {
        OffsetDateTime now = timeProvider.now();
        boolean signatureValid = vnPaySigner.verifySignature(params);
        String txnRef = params.get("vnp_TxnRef");

        Optional<PaymentAttemptEntity> attemptOpt =
                (txnRef != null) ? paymentAttemptRepository.findByTxnRef(txnRef) : Optional.empty();

        attemptOpt.ifPresent(attempt ->
                savePaymentEvent(attempt.getId(), "IPN_WEBHOOK", params, signatureValid, now)
        );

        if (!signatureValid) {
            return new VnPayIpnResponse("97", "Invalid Signature");
        }

        if (attemptOpt.isEmpty()) {
            return new VnPayIpnResponse("01", "Order not Found");
        }

        PaymentAttemptEntity attempt = attemptOpt.get();
        OrderEntity order = orderRepository.findById(attempt.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        // Kiểm tra số tiền vnp_Amount == attempt.amount * 100
        long expectedVnpAmount = attempt.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        long receivedVnpAmount;
        try {
            receivedVnpAmount = Long.parseLong(params.getOrDefault("vnp_Amount", "-1"));
        } catch (NumberFormatException ex) {
            return new VnPayIpnResponse("04", "Invalid Amount");
        }
        if (expectedVnpAmount != receivedVnpAmount) {
            return new VnPayIpnResponse("04", "Invalid Amount");
        }

        // AT-05: Chống IPN trùng lặp (Idempotency)
        if ("SUCCESS".equalsIgnoreCase(attempt.getStatus())
                || "LATE_PAID_RECONCILIATION".equalsIgnoreCase(attempt.getStatus())) {
            return new VnPayIpnResponse("00", "Confirm Success");
        }

        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", responseCode);
        attempt.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        attempt.setVnpPayDate(params.get("vnp_PayDate"));

        if (!"00".equals(responseCode) || !"00".equals(transactionStatus)) {
            attempt.setStatus("FAILED");
            paymentAttemptRepository.save(attempt);
            return new VnPayIpnResponse("00", "Confirm Success");
        }

        // Kiểm tra điều kiện Thanh toán đến muộn (AT-06):
        // 1. Đồng hồ máy chủ đã chạm hoặc qua giờ chốt (now >= cutoffAt)
        // 2. Đơn hàng không còn ở trạng thái PENDING_PAYMENT (ví dụ đã bị hủy do quá 30 phút bởi ReservationCleanupJob)
        // 3. Bản ghi giữ tồn (Reservation) đã quá hạn (expiresAt < now) hoặc không còn ACTIVE
        List<ReservationEntity> reservations = reservationRepository.findByOrderIdOrderByVariantIdAsc(order.getId());
        boolean reservationExpired = reservations.isEmpty() || reservations.stream().anyMatch(
                r -> r.getStatus() != ReservationStatus.ACTIVE || r.getExpiresAt().isBefore(now)
        );
        boolean cutoffPassed = !now.isBefore(order.getCutoffAt());
        boolean orderNoLongerPending = order.getOrderStatus() != OrderStatus.PENDING_PAYMENT;

        if (cutoffPassed || orderNoLongerPending || reservationExpired) {
            // Nếu còn dòng Reservation nào vẫn đang ACTIVE nhưng thực tế đã quá hạn khi IPN tới, giải phóng tồn ngay
            for (ReservationEntity r : reservations) {
                if (r.getStatus() == ReservationStatus.ACTIVE) {
                    inventoryBalanceRepository.releaseReservedStock(r.getCycleId(), r.getVariantId(), r.getReservedQty(), now);
                    r.setStatus(ReservationStatus.EXPIRED);
                    reservationRepository.save(r);
                }
            }
            attempt.setStatus("LATE_PAID_RECONCILIATION");
            attempt.setConfirmedAt(now);
            paymentAttemptRepository.save(attempt);

            if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setOrderStatus(OrderStatus.CANCELLED);
                order.setCancelReason(cutoffPassed ? "LATE_PAYMENT_AFTER_CUTOFF" : "PAYMENT_TIMEOUT");
            }
            order.setPaymentStatus(PaymentStatus.LATE_RECONCILIATION);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            saveOutboxNotification(
                    order.getId(),
                    "LATE_PAYMENT_RECONCILIATION_REQUIRED",
                    Map.of(
                            "orderId", order.getId(),
                            "orderCode", order.getOrderCode(),
                            "txnRef", attempt.getTxnRef(),
                            "amount", attempt.getAmount(),
                            "message", "Thanh toán VNPAY đến sau thời hạn giữ đơn/giờ chốt. Vui lòng chọn chuyển sang đợt kế tiếp hoặc nhận hoàn tiền 100%."
                    ),
                    now
            );
            return new VnPayIpnResponse("00", "Confirm Success");
        }

        // Luồng chuẩn: Thanh toán hợp lệ và đúng hạn trước giờ chốt -> Chuyển sang PAID_WAITING_CUTOFF & COMMITTED
        for (ReservationEntity r : reservations) {
            int updated = inventoryBalanceRepository.commitReservedStock(
                    r.getCycleId(), r.getVariantId(), r.getReservedQty(), now
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "Không thể cam kết tồn kho cho đơn hàng");
            }
            r.setStatus(ReservationStatus.COMMITTED);
            reservationRepository.save(r);
        }

        attempt.setStatus("SUCCESS");
        attempt.setConfirmedAt(now);
        paymentAttemptRepository.save(attempt);

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderStatus(OrderStatus.PAID_WAITING_CUTOFF);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        saveOutboxNotification(
                order.getId(),
                "ORDER_PAID_WAITING_CUTOFF",
                Map.of(
                        "orderId", order.getId(),
                        "orderCode", order.getOrderCode(),
                        "cutoffAt", order.getCutoffAt().toString()
                ),
                now
        );

        return new VnPayIpnResponse("00", "Confirm Success");
    }

    private void savePaymentEvent(Long attemptId,
                                  String source,
                                  Map<String, String> params,
                                  boolean signatureValid,
                                  OffsetDateTime now) {
        PaymentEventEntity event = new PaymentEventEntity();
        event.setPaymentAttemptId(attemptId);
        event.setEventSource(source);
        event.setRawPayload(toJson(params));
        event.setSignatureValid(signatureValid);
        event.setReceivedAt(now);
        paymentEventRepository.save(event);
    }

    private void saveOutboxNotification(Long orderId,
                                        String eventType,
                                        Map<String, Object> payload,
                                        OffsetDateTime now) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(String.valueOf(orderId));
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setStatus(OutboxEventStatus.PENDING);
        outbox.setCreatedAt(now);
        outboxEventRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record VnPayIpnResponse(
            String RspCode,
            String Message
    ) {
    }

    public record ReturnUrlStatusDto(
            Long orderId,
            String orderCode,
            String txnRef,
            String orderStatus,
            String paymentStatus,
            String message
    ) {
    }
}
