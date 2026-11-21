package vn.haui.o2ofood.modules.ordering;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.MoneyUtils;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.catalog.ProductEntity;
import vn.haui.o2ofood.modules.catalog.ProductRepository;
import vn.haui.o2ofood.modules.catalog.ProductVariantEntity;
import vn.haui.o2ofood.modules.catalog.ProductVariantRepository;
import vn.haui.o2ofood.modules.commission.CommissionPolicyEntity;
import vn.haui.o2ofood.modules.commission.CommissionPolicyRepository;
import vn.haui.o2ofood.modules.cycle.*;
import vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus;
import vn.haui.o2ofood.modules.identity.PickupPointEntity;
import vn.haui.o2ofood.modules.identity.PickupPointRepository;
import vn.haui.o2ofood.modules.payment.*;
import vn.haui.o2ofood.security.OwnershipSecurityService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private static final AtomicLong SEQ_COUNTER = new AtomicLong(1000);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PickupPointRepository pickupPointRepository;
    private final OrderCycleRepository orderCycleRepository;
    private final PointCycleRepository pointCycleRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CommissionPolicyRepository commissionPolicyRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final VnPaySigner vnPaySigner;
    private final OwnershipSecurityService ownershipSecurityService;
    private final TimeProvider timeProvider;
    private final EntityManager entityManager;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PickupPointRepository pickupPointRepository,
                        OrderCycleRepository orderCycleRepository,
                        PointCycleRepository pointCycleRepository,
                        InventoryBalanceRepository inventoryBalanceRepository,
                        ReservationRepository reservationRepository,
                        ProductRepository productRepository,
                        ProductVariantRepository productVariantRepository,
                        CommissionPolicyRepository commissionPolicyRepository,
                        PaymentAttemptRepository paymentAttemptRepository,
                        RefundRepository refundRepository,
                        VnPaySigner vnPaySigner,
                        OwnershipSecurityService ownershipSecurityService,
                        TimeProvider timeProvider,
                        EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pickupPointRepository = pickupPointRepository;
        this.orderCycleRepository = orderCycleRepository;
        this.pointCycleRepository = pointCycleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.commissionPolicyRepository = commissionPolicyRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.refundRepository = refundRepository;
        this.vnPaySigner = vnPaySigner;
        this.ownershipSecurityService = ownershipSecurityService;
        this.timeProvider = timeProvider;
        this.entityManager = entityManager;
    }

    /**
     * UC-C02, AT-01: Đặt hàng giữ tồn kho (Checkout) với Atomic Conditional Update chống bán vượt.
     * - Sắp xếp danh sách mặt hàng theo variantId ASC để tránh Deadlock.
     * - Trừ available_qty và tăng reserved_qty nguyên tử tại DB.
     * - Tạo Reservation với expires_at = MIN(now + payment_timeout_minutes, cutoff_at).
     * - Sinh URL thanh toán VNPAY Sandbox (HMAC-SHA512).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutResponse checkout(Long customerId, CheckoutRequest request, String clientIp) {
        OffsetDateTime now = timeProvider.now();

        PickupPointEntity pickupPoint = pickupPointRepository.findById(request.pickupPointId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy điểm nhận hàng"));
        if (!"ACTIVE".equalsIgnoreCase(pickupPoint.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Điểm nhận hàng hiện không hoạt động");
        }

        OrderCycleEntity cycle = orderCycleRepository.findById(request.cycleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao hàng"));
        if (!"OPEN".equalsIgnoreCase(cycle.getStatus()) || !now.isBefore(cycle.getCutoffAt())) {
            throw new BusinessException(ErrorCode.CUTOFF_PASSED, "Đợt giao hàng đã qua giờ chốt đặt đơn");
        }

        PointCycleEntity pointCycle = pointCycleRepository
                .findByCycleIdAndPickupPointId(cycle.getId(), pickupPoint.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Điểm nhận chưa mở cho đợt giao này"));
        if (!"OPEN".equalsIgnoreCase(pointCycle.getStatus()) || pointCycle.getCurrentOrders() >= pointCycle.getMaxOrders()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Điểm nhận đã đầy công suất trong đợt này");
        }

        // Bước 1 (Mục 4.2): Sắp xếp cartItems theo variantId ASC để tránh Deadlock
        List<CheckoutItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(CheckoutItemRequest::variantId))
                .toList();

        // Bước 2 (Mục 4.2): Thực thi Atomic Conditional Update cho từng mặt hàng
        List<ResolvedCartLine> resolvedLines = new ArrayList<>();
        BigDecimal subtotal = MoneyUtils.ZERO;

        for (CheckoutItemRequest itemReq : sortedItems) {
            ProductVariantEntity variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quy cách sản phẩm"));
            if (!Boolean.TRUE.equals(variant.getIsActive())) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Sản phẩm đã ngừng kinh doanh: " + variant.getPackName());
            }
            ProductEntity product = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));

            int updatedRows = inventoryBalanceRepository.reserveStockAtomically(
                    cycle.getId(), variant.getId(), itemReq.quantity(), now
            );
            if (updatedRows == 0) {
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        "Hết tồn kho khả dụng cho sản phẩm: " + product.getName() + " (" + variant.getPackName() + ")"
                );
            }

            BigDecimal lineTotal = MoneyUtils.multiply(variant.getUnitPrice(), itemReq.quantity());
            subtotal = MoneyUtils.normalize(subtotal.add(lineTotal));
            resolvedLines.add(new ResolvedCartLine(product, variant, itemReq.quantity(), lineTotal));
        }

        CommissionPolicyEntity policy = commissionPolicyRepository.findActivePolicies(now)
                .stream()
                .findFirst()
                .orElseGet(() -> commissionPolicyRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa cấu hình chính sách hoa hồng")));

        String orderCode = generateOrderCode(now);
        OrderEntity order = new OrderEntity();
        order.setOrderCode(orderCode);
        order.setCustomerId(customerId);
        order.setPickupPointId(pickupPoint.getId());
        order.setCycleId(cycle.getId());
        order.setCommissionPolicyId(policy.getId());
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(MoneyUtils.ZERO);
        order.setTotalAmount(subtotal);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_PACKING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setCutoffAt(cycle.getCutoffAt());
        order.setPickupStartAt(cycle.getPickupStartAt());
        order.setAutoCompleteAt(cycle.getAutoCompleteAt());
        order.setHasOpenIssue(false);
        order.setIsUnclaimedAtAgent(false);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        // Tính thời hạn giữ tồn: expires_at = MIN(now + payment_timeout_minutes, cutoff_at)
        int timeoutMinutes = cycle.getPaymentTimeoutMinutes() != null ? cycle.getPaymentTimeoutMinutes() : 30;
        OffsetDateTime candidateExpiry = now.plusMinutes(timeoutMinutes);
        OffsetDateTime expiresAt = candidateExpiry.isAfter(cycle.getCutoffAt()) ? cycle.getCutoffAt() : candidateExpiry;

        List<OrderItemDto> itemDtos = new ArrayList<>();
        for (ResolvedCartLine line : resolvedLines) {
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrderId(order.getId());
            orderItem.setVariantId(line.variant().getId());
            orderItem.setProductNameSnapshot(line.product().getName());
            orderItem.setPackNameSnapshot(line.variant().getPackName());
            orderItem.setUnitPriceSnapshot(MoneyUtils.normalize(line.variant().getUnitPrice()));
            orderItem.setQuantity(line.quantity());
            orderItem.setLineTotal(line.lineTotal());
            orderItem = orderItemRepository.save(orderItem);

            ReservationEntity reservation = new ReservationEntity();
            reservation.setOrderId(order.getId());
            reservation.setCycleId(cycle.getId());
            reservation.setVariantId(line.variant().getId());
            reservation.setReservedQty(line.quantity());
            reservation.setStatus(ReservationStatus.ACTIVE);
            reservation.setExpiresAt(expiresAt);
            reservationRepository.save(reservation);

            itemDtos.add(new OrderItemDto(
                    orderItem.getId(),
                    orderItem.getVariantId(),
                    orderItem.getProductNameSnapshot(),
                    orderItem.getPackNameSnapshot(),
                    orderItem.getUnitPriceSnapshot(),
                    orderItem.getQuantity(),
                    orderItem.getLineTotal()
            ));
        }

        String txnRef = order.getOrderCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.setOrderId(order.getId());
        attempt.setTxnRef(txnRef);
        attempt.setProvider("VNPAY");
        attempt.setAmount(order.getTotalAmount());
        attempt.setStatus("INITIATED");
        attempt.setCreatedAt(now);
        paymentAttemptRepository.save(attempt);

        String vnpayUrl = vnPaySigner.buildPaymentUrl(
                txnRef,
                order.getTotalAmount(),
                "Thanh toan don hang " + order.getOrderCode(),
                clientIp,
                now,
                expiresAt
        );

        return new CheckoutResponse(
                order.getId(),
                order.getOrderCode(),
                txnRef,
                order.getOrderStatus().name(),
                order.getPaymentStatus().name(),
                order.getTotalAmount(),
                expiresAt,
                order.getCutoffAt(),
                order.getPickupStartAt(),
                vnpayUrl,
                itemDtos
        );
    }

    /**
     * UC-C04, AT-03, AT-04: Khách hàng tự hủy đơn trước giờ chốt (23:59:59 Ngày D).
     * - Kiểm tra đồng hồ máy chủ (ServerTime < cutoffAt). Từ 00:00:00 trở đi trả lỗi 400 CUTOFF_PASSED.
     * - Dùng @Version khóa lạc quan chống đua dữ liệu với Job chốt đơn 00:00:00.
     * - Nếu PENDING_PAYMENT: giải phóng Reservation (RELEASED), hoàn lại available_qty.
     * - Nếu PAID_WAITING_CUTOFF: giải phóng Reservation (RELEASED), hoàn lại available_qty đúng 1 lần,
     *   tự động tạo bản ghi Refunds (APPROVED_PENDING_EXECUTION) cho 100% số tiền đã thu.
     */
    @Transactional
    public CancelOrderResponse cancelOrderByCustomer(Long customerId, Long orderId, String cancelReason) {
        OffsetDateTime now = timeProvider.now();

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        ownershipSecurityService.assertCustomerOwnsOrder(customerId, order);

        // AT-03: Kiểm tra mốc giờ chốt theo đồng hồ máy chủ
        if (!now.isBefore(order.getCutoffAt())) {
            throw new BusinessException(ErrorCode.CUTOFF_PASSED, "Đơn hàng đã qua giờ chốt 00:00 nên không thể tự hủy");
        }

        OrderStatus previousStatus = order.getOrderStatus();
        if (previousStatus == OrderStatus.CONFIRMED
                || previousStatus == OrderStatus.PROCESSING
                || previousStatus == OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CUTOFF_PASSED, "Đơn hàng đã qua giờ chốt 00:00 nên không thể tự hủy");
        }
        if (previousStatus == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Đơn hàng đã được hủy trước đó");
        }

        boolean wasPaid = (previousStatus == OrderStatus.PAID_WAITING_CUTOFF);

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelReason != null && !cancelReason.isBlank() ? cancelReason : "CUSTOMER_SELF_CANCEL");
        if (wasPaid) {
            order.setPaymentStatus(PaymentStatus.REFUND_APPROVED);
        }
        order.setUpdatedAt(now);

        // Flush ngay lập tức để kích hoạt kiểm tra @Version (AT-04: chống đua dữ liệu với Job 00:00:00)
        order = orderRepository.saveAndFlush(order);

        List<ReservationEntity> reservations = reservationRepository.findByOrderIdOrderByVariantIdAsc(order.getId());
        for (ReservationEntity reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                inventoryBalanceRepository.releaseReservedStock(
                        reservation.getCycleId(), reservation.getVariantId(), reservation.getReservedQty(), now
                );
                reservation.setStatus(ReservationStatus.RELEASED);
                reservationRepository.save(reservation);
            } else if (reservation.getStatus() == ReservationStatus.COMMITTED) {
                inventoryBalanceRepository.releaseCommittedStock(
                        reservation.getCycleId(), reservation.getVariantId(), reservation.getReservedQty(), now
                );
                reservation.setStatus(ReservationStatus.RELEASED);
                reservationRepository.save(reservation);
            }
        }

        Long refundId = null;
        String refundStatus = null;
        if (wasPaid) {
            RefundEntity refund = new RefundEntity();
            refund.setOrderId(order.getId());
            refund.setRefundReason("CUSTOMER_CANCEL_BEFORE_CUTOFF");
            refund.setAmount(order.getTotalAmount());
            refund.setStatus("APPROVED_PENDING_EXECUTION");
            refund.setCreatedAt(now);
            refund = refundRepository.save(refund);
            refundId = refund.getId();
            refundStatus = refund.getStatus();
        }

        entityManager.flush();

        return new CancelOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getOrderStatus().name(),
                order.getPaymentStatus().name(),
                refundId,
                refundStatus,
                now
        );
    }

    /**
     * UC-C03 & AT-06: Khách hàng lựa chọn phương án xử lý khi thanh toán VNPAY đến muộn (LATE_RECONCILIATION):
     * (a) ROLLOVER_NEXT_CYCLE: Chuyển sang đợt giao kế tiếp nếu còn tồn kho, hoặc
     * (b) REFUND_100_PERCENT: Nhận hoàn tiền 100%.
     */
    @Transactional
    public OrderSummaryDto resolveLatePayment(Long customerId,
                                              Long orderId,
                                              LatePaymentResolutionRequest request) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        ownershipSecurityService.assertCustomerOwnsOrder(customerId, order);

        if (order.getPaymentStatus() != PaymentStatus.LATE_RECONCILIATION) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Đơn hàng không ở trạng thái chờ đối soát thanh toán muộn");
        }

        if ("REFUND".equalsIgnoreCase(request.action())) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.REFUND_APPROVED);
            order.setCancelReason("LATE_PAYMENT_CUSTOMER_CHOSE_REFUND");
            order.setUpdatedAt(now);
            orderRepository.save(order);

            RefundEntity refund = new RefundEntity();
            refund.setOrderId(order.getId());
            refund.setRefundReason("LATE_PAYMENT_REFUND");
            refund.setAmount(order.getTotalAmount());
            refund.setStatus("APPROVED_PENDING_EXECUTION");
            refund.setCreatedAt(now);
            refundRepository.save(refund);
        } else if ("ROLLOVER".equalsIgnoreCase(request.action())) {
            OrderCycleEntity nextCycle = orderCycleRepository.findOpenCyclesAfter(now)
                    .stream()
                    .filter(c -> !c.getId().equals(order.getCycleId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao kế tiếp đang mở"));

            List<OrderItemEntity> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
            for (OrderItemEntity item : items) {
                int reserved = inventoryBalanceRepository.reserveStockAtomically(
                        nextCycle.getId(), item.getVariantId(), item.getQuantity(), now
                );
                if (reserved == 0) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Đợt giao kế tiếp không đủ tồn kho cho món: " + item.getProductNameSnapshot());
                }
                inventoryBalanceRepository.commitReservedStock(
                        nextCycle.getId(), item.getVariantId(), item.getQuantity(), now
                );
                ReservationEntity newRes = new ReservationEntity();
                newRes.setOrderId(order.getId());
                newRes.setCycleId(nextCycle.getId());
                newRes.setVariantId(item.getVariantId());
                newRes.setReservedQty(item.getQuantity());
                newRes.setStatus(ReservationStatus.COMMITTED);
                newRes.setExpiresAt(nextCycle.getCutoffAt());
                reservationRepository.save(newRes);
            }

            order.setCycleId(nextCycle.getId());
            order.setCutoffAt(nextCycle.getCutoffAt());
            order.setPickupStartAt(nextCycle.getPickupStartAt());
            order.setAutoCompleteAt(nextCycle.getAutoCompleteAt());
            order.setOrderStatus(OrderStatus.PAID_WAITING_CUTOFF);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setCancelReason(null);
            order.setUpdatedAt(now);
            orderRepository.save(order);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phương án xử lý không hợp lệ (chỉ chấp nhận ROLLOVER hoặc REFUND)");
        }

        return toOrderSummaryDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryDto> getCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toOrderSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderDetailForCustomer(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        ownershipSecurityService.assertCustomerOwnsOrder(customerId, order);
        List<OrderItemDto> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(i -> new OrderItemDto(
                        i.getId(),
                        i.getVariantId(),
                        i.getProductNameSnapshot(),
                        i.getPackNameSnapshot(),
                        i.getUnitPriceSnapshot(),
                        i.getQuantity(),
                        i.getLineTotal()
                ))
                .toList();
        return new OrderDetailDto(
                order.getId(),
                order.getOrderCode(),
                order.getCustomerId(),
                order.getPickupPointId(),
                order.getCycleId(),
                order.getOrderStatus().name(),
                order.getFulfillmentStatus().name(),
                order.getPaymentStatus().name(),
                order.getTotalAmount(),
                order.getCutoffAt(),
                order.getPickupStartAt(),
                order.getAutoCompleteAt(),
                order.getConfirmedAt(),
                order.getCompletedAt(),
                order.getCompletionType() != null ? order.getCompletionType().name() : null,
                order.getPhysicalHandedOverAt(),
                order.getCustomerConfirmedAt(),
                order.getHasOpenIssue(),
                order.getIsUnclaimedAtAgent(),
                items
        );
    }

    private OrderSummaryDto toOrderSummaryDto(OrderEntity order) {
        return new OrderSummaryDto(
                order.getId(),
                order.getOrderCode(),
                order.getPickupPointId(),
                order.getCycleId(),
                order.getOrderStatus().name(),
                order.getFulfillmentStatus().name(),
                order.getPaymentStatus().name(),
                order.getTotalAmount(),
                order.getCutoffAt(),
                order.getPickupStartAt(),
                order.getCreatedAt()
        );
    }

    private String generateOrderCode(OffsetDateTime now) {
        String datePart = String.format("%04d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        long seq = SEQ_COUNTER.incrementAndGet();
        return "ORD-" + datePart + "-" + seq;
    }

    private record ResolvedCartLine(
            ProductEntity product,
            ProductVariantEntity variant,
            int quantity,
            BigDecimal lineTotal
    ) {
    }

    public record CheckoutItemRequest(
            Long variantId,
            int quantity
    ) {
    }

    public record CheckoutRequest(
            Long pickupPointId,
            Long cycleId,
            List<CheckoutItemRequest> items
    ) {
    }

    public record OrderItemDto(
            Long id,
            Long variantId,
            String productName,
            String packName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal
    ) {
    }

    public record CheckoutResponse(
            Long orderId,
            String orderCode,
            String txnRef,
            String orderStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            OffsetDateTime reservationExpiresAt,
            OffsetDateTime cutoffAt,
            OffsetDateTime pickupStartAt,
            String vnpayPaymentUrl,
            List<OrderItemDto> items
    ) {
    }

    public record CancelOrderResponse(
            Long orderId,
            String orderCode,
            String orderStatus,
            String paymentStatus,
            Long refundId,
            String refundStatus,
            OffsetDateTime cancelledAt
    ) {
    }

    public record LatePaymentResolutionRequest(
            String action
    ) {
    }

    public record OrderSummaryDto(
            Long orderId,
            String orderCode,
            Long pickupPointId,
            Long cycleId,
            String orderStatus,
            String fulfillmentStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            OffsetDateTime cutoffAt,
            OffsetDateTime pickupStartAt,
            OffsetDateTime createdAt
    ) {
    }

    public record OrderDetailDto(
            Long orderId,
            String orderCode,
            Long customerId,
            Long pickupPointId,
            Long cycleId,
            String orderStatus,
            String fulfillmentStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            OffsetDateTime cutoffAt,
            OffsetDateTime pickupStartAt,
            OffsetDateTime autoCompleteAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime completedAt,
            String completionType,
            OffsetDateTime physicalHandedOverAt,
            OffsetDateTime customerConfirmedAt,
            Boolean hasOpenIssue,
            Boolean isUnclaimedAtAgent,
            List<OrderItemDto> items
    ) {
    }
}
