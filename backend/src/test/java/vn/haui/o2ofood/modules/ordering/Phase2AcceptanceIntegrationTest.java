package vn.haui.o2ofood.modules.ordering;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
import vn.haui.o2ofood.modules.identity.PickupPointEntity;
import vn.haui.o2ofood.modules.identity.PickupPointRepository;
import vn.haui.o2ofood.modules.identity.UserEntity;
import vn.haui.o2ofood.modules.identity.UserRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.payment.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ Kiểm Thử Nghiệm Thu Bắt Buộc Phase 2 (AT-01 -> AT-06) theo Mục 7 của tài liệu đặc tả.
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase2AcceptanceIntegrationTest {

    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private OrderService orderService;
    @Autowired
    private VnPayService vnPayService;
    @Autowired
    private VnPaySigner vnPaySigner;
    @Autowired
    private ReservationCleanupJob reservationCleanupJob;
    @Autowired
    private AutoConfirmOrdersJob autoConfirmOrdersJob;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PickupPointRepository pickupPointRepository;
    @Autowired
    private OrderCycleRepository orderCycleRepository;
    @Autowired
    private PointCycleRepository pointCycleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private CommissionPolicyRepository commissionPolicyRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private JobRunRepository jobRunRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customer1Id;
    private Long customer2Id;
    private Long pickupPointId;
    private Long cycleD1Id;
    private Long cycleD2Id;
    private Long variantId;

    private static final OffsetDateTime BASE_ORDER_TIME = OffsetDateTime.parse("2026-11-17T10:00:00+07:00");
    private static final OffsetDateTime CUTOFF_TIME_D = OffsetDateTime.parse("2026-11-17T23:59:59+07:00");
    private static final OffsetDateTime AUTO_CONFIRM_TIME_D1 = OffsetDateTime.parse("2026-11-18T00:00:00+07:00");

    @BeforeEach
    void setUp() {
        timeProvider.setFixedTime(BASE_ORDER_TIME);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP NOT NULL,
                    locked_at TIMESTAMP NOT NULL,
                    locked_by VARCHAR(255) NOT NULL
                )
                """);

        outboxEventRepository.deleteAll();
        jobRunRepository.deleteAll();
        refundRepository.deleteAll();
        paymentEventRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        reservationRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        pointCycleRepository.deleteAll();
        orderCycleRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        pickupPointRepository.deleteAll();
        commissionPolicyRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity c1 = new UserEntity();
        c1.setEmail("c1@test.vn");
        c1.setPhone("0900000001");
        c1.setPasswordHash("hash");
        c1.setFullName("Khách Hàng 1");
        c1.setStatus("ACTIVE");
        c1.setCreatedAt(BASE_ORDER_TIME);
        customer1Id = userRepository.save(c1).getId();

        UserEntity c2 = new UserEntity();
        c2.setEmail("c2@test.vn");
        c2.setPhone("0900000002");
        c2.setPasswordHash("hash");
        c2.setFullName("Khách Hàng 2");
        c2.setStatus("ACTIVE");
        c2.setCreatedAt(BASE_ORDER_TIME);
        customer2Id = userRepository.save(c2).getId();

        PickupPointEntity point = new PickupPointEntity();
        point.setCode("PP-TEST-01");
        point.setName("Đại lý Cầu Giấy");
        point.setAddress("125 Xuân Thủy, Cầu Giấy, Hà Nội");
        point.setLatitude(new BigDecimal("21.036820"));
        point.setLongitude(new BigDecimal("105.782460"));
        point.setColdStorageCapacity(150);
        point.setStatus("ACTIVE");
        pickupPointId = pickupPointRepository.save(point).getId();

        CommissionPolicyEntity policy = new CommissionPolicyEntity();
        policy.setPolicyCode("POL-TEST-5PCT");
        policy.setCalcType("PERCENTAGE_NET_MERCHANDISE");
        policy.setPercentageRate(new BigDecimal("5.00"));
        policy.setEffectiveFrom(BASE_ORDER_TIME.minusDays(10));
        commissionPolicyRepository.save(policy);

        OrderCycleEntity cycle1 = new OrderCycleEntity();
        cycle1.setCycleCode("CYCLE-2026-11-18");
        cycle1.setDeliveryDate(LocalDate.of(2026, 11, 18));
        cycle1.setOpenAt(OffsetDateTime.parse("2026-11-17T00:00:00+07:00"));
        cycle1.setCutoffAt(CUTOFF_TIME_D);
        cycle1.setAutoConfirmAt(AUTO_CONFIRM_TIME_D1);
        cycle1.setWarehousePackTargetAt(OffsetDateTime.parse("2026-11-18T08:00:00+07:00"));
        cycle1.setDispatchStartAt(OffsetDateTime.parse("2026-11-18T08:00:00+07:00"));
        cycle1.setPickupStartAt(OffsetDateTime.parse("2026-11-18T16:00:00+07:00"));
        cycle1.setAutoCompleteAt(OffsetDateTime.parse("2026-11-19T00:00:00+07:00"));
        cycle1.setPaymentTimeoutMinutes(30);
        cycle1.setStatus("OPEN");
        cycleD1Id = orderCycleRepository.save(cycle1).getId();

        OrderCycleEntity cycle2 = new OrderCycleEntity();
        cycle2.setCycleCode("CYCLE-2026-11-19");
        cycle2.setDeliveryDate(LocalDate.of(2026, 11, 19));
        cycle2.setOpenAt(OffsetDateTime.parse("2026-11-18T00:00:00+07:00"));
        cycle2.setCutoffAt(OffsetDateTime.parse("2026-11-18T23:59:59+07:00"));
        cycle2.setAutoConfirmAt(OffsetDateTime.parse("2026-11-19T00:00:00+07:00"));
        cycle2.setWarehousePackTargetAt(OffsetDateTime.parse("2026-11-19T08:00:00+07:00"));
        cycle2.setDispatchStartAt(OffsetDateTime.parse("2026-11-19T08:00:00+07:00"));
        cycle2.setPickupStartAt(OffsetDateTime.parse("2026-11-19T16:00:00+07:00"));
        cycle2.setAutoCompleteAt(OffsetDateTime.parse("2026-11-20T00:00:00+07:00"));
        cycle2.setPaymentTimeoutMinutes(30);
        cycle2.setStatus("OPEN");
        cycleD2Id = orderCycleRepository.save(cycle2).getId();

        PointCycleEntity pc1 = new PointCycleEntity();
        pc1.setCycleId(cycleD1Id);
        pc1.setPickupPointId(pickupPointId);
        pc1.setMaxOrders(150);
        pc1.setCurrentOrders(0);
        pc1.setStatus("OPEN");
        pointCycleRepository.save(pc1);

        PointCycleEntity pc2 = new PointCycleEntity();
        pc2.setCycleId(cycleD2Id);
        pc2.setPickupPointId(pickupPointId);
        pc2.setMaxOrders(150);
        pc2.setCurrentOrders(0);
        pc2.setStatus("OPEN");
        pointCycleRepository.save(pc2);

        ProductEntity product = new ProductEntity();
        product.setSkuPrefix("MEAT-PORK-TEST");
        product.setName("Thịt ba chỉ heo VietGAP");
        product.setCategory("Thịt tươi sống");
        product.setStorageType("CHILLED");
        product.setMinShelfLifeDaysAtPickup(2);
        product.setIsActive(true);
        product = productRepository.save(product);

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProductId(product.getId());
        variant.setSku("MEAT-PORK-TEST-500G");
        variant.setPackName("Khay 500g");
        variant.setPackWeightGrams(500);
        variant.setUnitPrice(MoneyUtils.of(85000));
        variant.setIsActive(true);
        variantId = productVariantRepository.save(variant).getId();

        InventoryBalanceEntity bal1 = new InventoryBalanceEntity();
        bal1.setCycleId(cycleD1Id);
        bal1.setVariantId(variantId);
        bal1.setAvailableQty(10);
        bal1.setReservedQty(0);
        bal1.setCommittedQty(0);
        bal1.setUpdatedAt(BASE_ORDER_TIME);
        inventoryBalanceRepository.save(bal1);

        InventoryBalanceEntity bal2 = new InventoryBalanceEntity();
        bal2.setCycleId(cycleD2Id);
        bal2.setVariantId(variantId);
        bal2.setAvailableQty(10);
        bal2.setReservedQty(0);
        bal2.setCommittedQty(0);
        bal2.setUpdatedAt(BASE_ORDER_TIME);
        inventoryBalanceRepository.save(bal2);
    }

    @AfterEach
    void tearDown() {
        timeProvider.resetToSystemClock();
    }

    @Test
    @DisplayName("AT-01: Hai khách hàng cùng bấm thanh toán 1 gói hàng cuối cùng trong đợt")
    void at01_twoCustomersConcurrentCheckoutLastItem_onlyOneSucceeds() throws Exception {
        // Đặt tồn khả dụng đúng bằng 1 gói cuối cùng
        InventoryBalanceEntity balance = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        balance.setAvailableQty(1);
        inventoryBalanceRepository.saveAndFlush(balance);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.checkout(
                        customer1Id,
                        new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                                List.of(new OrderService.CheckoutItemRequest(variantId, 1))),
                        "127.0.0.1"
                );
                successCount.incrementAndGet();
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK
                        && ex.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT) {
                    insufficientStockCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.checkout(
                        customer2Id,
                        new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                                List.of(new OrderService.CheckoutItemRequest(variantId, 1))),
                        "127.0.0.1"
                );
                successCount.incrementAndGet();
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK
                        && ex.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT) {
                    insufficientStockCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Future<?> f1 = executor.submit(task1);
        Future<?> f2 = executor.submit(task2);
        readyLatch.await();
        startLatch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ đúng 1 khách hàng tạo được Reservation");
        assertEquals(1, insufficientStockCount.get(), "Khách còn lại phải nhận mã lỗi 409 INSUFFICIENT_STOCK");

        InventoryBalanceEntity afterBalance = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(0, afterBalance.getAvailableQty(), "available_qty tuyệt đối không âm và bằng 0");
        assertEquals(1, afterBalance.getReservedQty(), "reserved_qty tăng đúng 1");
        assertEquals(1, reservationRepository.findAll().size());
    }

    @Test
    @DisplayName("AT-02: Đơn tạo chờ thanh toán quá 30 phút (hoặc chạm mốc 23:59:59) tự hủy và trả tồn đúng 1 lần")
    void at02_reservationTimeoutAndCutoffCap_autoCancelledAndRestoresStockOnce() {
        // Trường hợp 1: Tạo đơn lúc 23:45 -> expires_at bị chặn trần bởi cutoff_at (23:59:59)
        OffsetDateTime orderAt2345 = OffsetDateTime.parse("2026-11-17T23:45:00+07:00");
        timeProvider.setFixedTime(orderAt2345);

        OrderService.CheckoutResponse checkout = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 3))),
                "127.0.0.1"
        );
        assertEquals(CUTOFF_TIME_D, checkout.reservationExpiresAt(),
                "Đơn tạo lúc 23:45 chỉ được giữ tồn tối đa đến 23:59:59 (chặn trần bởi cutoff_at)");

        InventoryBalanceEntity midBalance = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(7, midBalance.getAvailableQty());
        assertEquals(3, midBalance.getReservedQty());

        // Khi đồng hồ máy chủ qua mốc hết hạn giữ tồn (00:00:05) -> ReservationCleanupJob chạy
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T00:00:05+07:00"));
        int cleanedFirstRun = reservationCleanupJob.cleanupExpiredReservations();
        int cleanedSecondRun = reservationCleanupJob.cleanupExpiredReservations();

        assertEquals(1, cleanedFirstRun);
        assertEquals(0, cleanedSecondRun, "Chạy lại Job lần 2 không được cộng trả tồn lần hai");

        OrderEntity updatedOrder = orderRepository.findById(checkout.orderId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, updatedOrder.getOrderStatus());
        assertEquals("PAYMENT_TIMEOUT", updatedOrder.getCancelReason());

        InventoryBalanceEntity finalBalance = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(10, finalBalance.getAvailableQty(), "available_qty được hoàn trả đúng 1 lần về 10");
        assertEquals(0, finalBalance.getReservedQty());
    }

    @Test
    @DisplayName("AT-03: Khách tự hủy đơn đã thanh toán lúc 23:59:58 (thành công + hoàn tiền 100%) và lúc 00:00:00 (lỗi 400 CUTOFF_PASSED)")
    void at03_customerCancelAt235958SucceedsWithRefund_andAt000000FailsWithCutoffPassed() {
        // Tạo 2 đơn hàng và thanh toán thành công qua VNPAY IPN lúc 22:00:00
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T22:00:00+07:00"));

        OrderService.CheckoutResponse orderA = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 2))),
                "127.0.0.1"
        );
        OrderService.CheckoutResponse orderB = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 1))),
                "127.0.0.1"
        );

        vnPayService.processIpnWebhook(buildValidIpnParams(orderA.txnRef(), orderA.totalAmount(), "00"));
        vnPayService.processIpnWebhook(buildValidIpnParams(orderB.txnRef(), orderB.totalAmount(), "00"));

        // 1. Lúc 23:59:58: Khách tự hủy đơn A -> Thành công, trả lại 2 gói tồn kho và tạo Refund 100%
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T23:59:58+07:00"));
        OrderService.CancelOrderResponse cancelRes = orderService.cancelOrderByCustomer(
                customer1Id, orderA.orderId(), "Đổi ý trước giờ chốt"
        );
        assertEquals("CANCELLED", cancelRes.orderStatus());
        assertEquals("REFUND_APPROVED", cancelRes.paymentStatus());
        assertNotNull(cancelRes.refundId());
        assertEquals("APPROVED_PENDING_EXECUTION", cancelRes.refundStatus());

        List<RefundEntity> refundsA = refundRepository.findByOrderIdOrderByIdAsc(orderA.orderId());
        assertEquals(1, refundsA.size());
        assertEquals(orderA.totalAmount(), refundsA.get(0).getAmount());

        InventoryBalanceEntity balanceAfterCancelA = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(9, balanceAfterCancelA.getAvailableQty(), "Đã hoàn lại 2 gói của đơn A về available_qty");
        assertEquals(1, balanceAfterCancelA.getCommittedQty(), "Còn 1 gói committed của đơn B");

        // 2. Lúc 00:00:00: Khách bấm hủy đơn B -> Trả lỗi 400 CUTOFF_PASSED
        timeProvider.setFixedTime(AUTO_CONFIRM_TIME_D1);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.cancelOrderByCustomer(customer1Id, orderB.orderId(), "Muốn hủy lúc 00:00:00")
        );
        assertEquals(ErrorCode.CUTOFF_PASSED, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getErrorCode().getHttpStatus());
    }

    @Test
    @DisplayName("AT-04: Khách bấm Hủy đơn đồng thời với Job chốt đơn 00:00:00 (@Version chống đua dữ liệu)")
    void at04_concurrentCustomerCancelAndAutoConfirmJob_onlyOneWinsViaOptimisticLock() {
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T23:00:00+07:00"));
        OrderService.CheckoutResponse checkout = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 2))),
                "127.0.0.1"
        );
        vnPayService.processIpnWebhook(buildValidIpnParams(checkout.txnRef(), checkout.totalAmount(), "00"));

        // Đọc cùng 1 bản ghi OrderEntity (version hiện tại) ở 2 tiến trình đua nhau lúc chuyển giao 23:59:59 -> 00:00:00
        OrderEntity snapshotForCancel = orderRepository.findById(checkout.orderId()).orElseThrow();
        OrderEntity snapshotForAutoConfirm = orderRepository.findById(checkout.orderId()).orElseThrow();
        assertEquals(snapshotForCancel.getVersion(), snapshotForAutoConfirm.getVersion());

        // Giả lập Job chốt đơn vừa cập nhật thành CONFIRMED và tăng version
        snapshotForAutoConfirm.setOrderStatus(OrderStatus.CONFIRMED);
        snapshotForAutoConfirm.setConfirmedAt(AUTO_CONFIRM_TIME_D1);
        snapshotForAutoConfirm.setUpdatedAt(AUTO_CONFIRM_TIME_D1);
        orderRepository.saveAndFlush(snapshotForAutoConfirm);

        // Tiến trình Hủy đơn giữ bản ghi cũ (cùng @Version trước đó) cố gắng ghi đè sang CANCELLED -> Bị chặn bởi OptimisticLockingFailureException
        snapshotForCancel.setOrderStatus(OrderStatus.CANCELLED);
        assertThrows(OptimisticLockingFailureException.class, () ->
                orderRepository.saveAndFlush(snapshotForCancel)
        );

        // Kiểm tra thông qua OrderService: khi đơn đã được Job chuyển sang CONFIRMED, gọi API hủy đơn sẽ bị từ chối nhất quán
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T23:59:59+07:00"));
        BusinessException serviceEx = assertThrows(BusinessException.class, () ->
                orderService.cancelOrderByCustomer(customer1Id, checkout.orderId(), "Hủy trễ khi Job vừa thắng")
        );
        assertEquals(ErrorCode.CUTOFF_PASSED, serviceEx.getErrorCode());

        // Đảm bảo tuyệt đối không có lệnh hoàn tiền nào được tạo ra và đơn nằm trong danh sách đóng gói của kho
        assertTrue(refundRepository.findByOrderIdOrderByIdAsc(checkout.orderId()).isEmpty(),
                "Đơn đã xác nhận cho kho thì không bao giờ được sinh lệnh hoàn tiền");
        List<OrderEntity> warehouseOrders = orderRepository.findConfirmedOrdersForWarehousePacking(cycleD1Id);
        assertEquals(1, warehouseOrders.size());
        assertEquals(checkout.orderId(), warehouseOrders.get(0).getId());
    }

    @Test
    @DisplayName("AT-05: VNPAY gọi IPN lặp lại 3 lần hoặc sai chữ ký vnp_SecureHash")
    void at05_vnpayIpnInvalidSignatureRejected_andDuplicateValidIpn3TimesIdempotent() {
        OrderService.CheckoutResponse checkout = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 2))),
                "127.0.0.1"
        );

        // 1. Gửi IPN với chữ ký vnp_SecureHash giả mạo -> Bị từ chối ngay (RspCode = 97)
        Map<String, String> tamperedParams = buildValidIpnParams(checkout.txnRef(), checkout.totalAmount(), "00");
        tamperedParams.put("vnp_SecureHash", "000000000000000000000000000000000000000000000000000000000000dead");
        VnPayService.VnPayIpnResponse badSigRes = vnPayService.processIpnWebhook(tamperedParams);
        assertEquals("97", badSigRes.RspCode());

        OrderEntity orderAfterBadSig = orderRepository.findById(checkout.orderId()).orElseThrow();
        assertEquals(OrderStatus.PENDING_PAYMENT, orderAfterBadSig.getOrderStatus(),
                "Sai chữ ký tuyệt đối không làm thay đổi trạng thái đơn hàng");

        // 2. Gửi IPN hợp lệ lặp lại 3 lần liên tiếp -> Chỉ ghi nhận thanh toán và chuyển tồn 1 lần duy nhất
        Map<String, String> validParams = buildValidIpnParams(checkout.txnRef(), checkout.totalAmount(), "00");
        VnPayService.VnPayIpnResponse res1 = vnPayService.processIpnWebhook(validParams);
        VnPayService.VnPayIpnResponse res2 = vnPayService.processIpnWebhook(validParams);
        VnPayService.VnPayIpnResponse res3 = vnPayService.processIpnWebhook(validParams);

        assertEquals("00", res1.RspCode());
        assertEquals("00", res2.RspCode());
        assertEquals("00", res3.RspCode());

        OrderEntity paidOrder = orderRepository.findById(checkout.orderId()).orElseThrow();
        assertEquals(OrderStatus.PAID_WAITING_CUTOFF, paidOrder.getOrderStatus());
        assertEquals(PaymentStatus.PAID, paidOrder.getPaymentStatus());

        InventoryBalanceEntity balance = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(8, balance.getAvailableQty(), "available_qty giữ nguyên 8, không bị trừ lặp");
        assertEquals(0, balance.getReservedQty(), "reserved_qty đã chuyển sang committed_qty");
        assertEquals(2, balance.getCommittedQty(), "committed_qty tăng đúng 2 gói (1 lần duy nhất)");
    }

    @Test
    @DisplayName("AT-06: VNPAY gửi IPN thành công muộn sau khi hết hạn giữ tồn 30 phút hoặc sau 00:00 -> LATE_PAID_RECONCILIATION")
    void at06_lateVnpayIpnAfterTimeoutOrCutoff_recordsLatePaidReconciliationAndAllowsCustomerChoice() {
        // Tạo đơn lúc 10:00:00 (hạn giữ tồn là 10:30:00)
        OrderService.CheckoutResponse checkout = orderService.checkout(
                customer1Id,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, 2))),
                "127.0.0.1"
        );

        // Đến 10:35:00 (quá 30 phút), ReservationCleanupJob chạy hủy giữ tồn
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T10:35:00+07:00"));
        reservationCleanupJob.cleanupExpiredReservations();

        // Lúc 10:40:00, VNPAY gửi IPN báo thanh toán thành công muộn
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-17T10:40:00+07:00"));
        VnPayService.VnPayIpnResponse ipnRes = vnPayService.processIpnWebhook(
                buildValidIpnParams(checkout.txnRef(), checkout.totalAmount(), "00")
        );
        assertEquals("00", ipnRes.RspCode());

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByTxnRef(checkout.txnRef()).orElseThrow();
        assertEquals("LATE_PAID_RECONCILIATION", attempt.getStatus());

        OrderEntity lateOrder = orderRepository.findById(checkout.orderId()).orElseThrow();
        assertEquals(cycleD1Id, lateOrder.getCycleId(), "Không được tự ý đổi đợt giao khi chưa hỏi ý kiến khách");
        assertEquals(PaymentStatus.LATE_RECONCILIATION, lateOrder.getPaymentStatus());

        // Khách hàng chọn chuyển sang đợt giao kế tiếp (ROLLOVER sang CYCLE-2026-11-19)
        OrderService.OrderSummaryDto resolved = orderService.resolveLatePayment(
                customer1Id,
                checkout.orderId(),
                new OrderService.LatePaymentResolutionRequest("ROLLOVER")
        );
        assertEquals(cycleD2Id, resolved.cycleId(), "Sau khi khách chọn ROLLOVER, đơn chuyển sang đợt kế tiếp D+2");
        assertEquals("PAID_WAITING_CUTOFF", resolved.orderStatus());
        assertEquals("PAID", resolved.paymentStatus());
    }

    private Map<String, String> buildValidIpnParams(String txnRef, BigDecimal amountVnd, String responseCode) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "DEMO_O2OFOOD");
        params.put("vnp_Amount", String.valueOf(amountVnd.multiply(BigDecimal.valueOf(100)).longValue()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + txnRef);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_PayDate", "20261117220000");
        String signature = vnPaySigner.signParams(params);
        params.put("vnp_SecureHash", signature);
        return params;
    }
}
