package vn.haui.o2ofood.modules.fulfillment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.haui.o2ofood.common.MoneyUtils;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.catalog.*;
import vn.haui.o2ofood.modules.commission.*;
import vn.haui.o2ofood.modules.cycle.*;
import vn.haui.o2ofood.modules.exception.*;
import vn.haui.o2ofood.modules.identity.PickupPointEntity;
import vn.haui.o2ofood.modules.identity.PickupPointRepository;
import vn.haui.o2ofood.modules.identity.UserEntity;
import vn.haui.o2ofood.modules.identity.UserRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.ordering.*;
import vn.haui.o2ofood.modules.payment.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ Kiểm Thử Nghiệm Thu Bắt Buộc Phase 3 (AT-07 -> AT-11) theo Mục 7 của tài liệu đặc tả.
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase3AcceptanceIntegrationTest {

    @Autowired
    private TimeProvider timeProvider;
    @Autowired
    private OrderService orderService;
    @Autowired
    private VnPayService vnPayService;
    @Autowired
    private VnPaySigner vnPaySigner;
    @Autowired
    private AutoConfirmOrdersJob autoConfirmOrdersJob;
    @Autowired
    private AutoCompleteOrdersJob autoCompleteOrdersJob;
    @Autowired
    private WarehouseFefoService warehouseFefoService;
    @Autowired
    private FulfillmentService fulfillmentService;
    @Autowired
    private ExceptionReturnService exceptionReturnService;
    @Autowired
    private CommissionService commissionService;

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
    private LotRepository lotRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private CommissionPolicyRepository commissionPolicyRepository;
    @Autowired
    private CommissionLedgerRepository commissionLedgerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderLotAllocationRepository orderLotAllocationRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private TripStopRepository tripStopRepository;
    @Autowired
    private PackageRepository packageRepository;
    @Autowired
    private HandoverRecordRepository handoverRecordRepository;
    @Autowired
    private HandoverItemRepository handoverItemRepository;
    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private ReturnRequestRepository returnRequestRepository;
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

    private Long adminUserId;
    private Long warehouseUserId;
    private Long driverUserId;
    private Long agentUserId;
    private Long customerUserId;
    private Long pickupPointId;
    private Long cycleD1Id;
    private Long variantId;

    private static final OffsetDateTime ORDER_TIME_D = OffsetDateTime.parse("2026-11-17T14:00:00+07:00");
    private static final OffsetDateTime CUTOFF_TIME_D = OffsetDateTime.parse("2026-11-17T23:59:59+07:00");
    private static final OffsetDateTime AUTO_CONFIRM_D1 = OffsetDateTime.parse("2026-11-18T00:00:00+07:00");
    private static final OffsetDateTime DISPATCH_D1_0800 = OffsetDateTime.parse("2026-11-18T08:00:00+07:00");
    private static final OffsetDateTime HANDOVER_D1_1530 = OffsetDateTime.parse("2026-11-18T15:30:00+07:00");
    private static final OffsetDateTime PICKUP_START_D1_1600 = OffsetDateTime.parse("2026-11-18T16:00:00+07:00");
    private static final OffsetDateTime AUTO_COMPLETE_D2_0000 = OffsetDateTime.parse("2026-11-19T00:00:00+07:00");

    @BeforeEach
    void setUp() {
        timeProvider.setFixedTime(ORDER_TIME_D);

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
        commissionLedgerRepository.deleteAll();
        refundRepository.deleteAll();
        returnRequestRepository.deleteAll();
        issueRepository.deleteAll();
        handoverItemRepository.deleteAll();
        handoverRecordRepository.deleteAll();
        packageRepository.deleteAll();
        tripStopRepository.deleteAll();
        tripRepository.deleteAll();
        orderLotAllocationRepository.deleteAll();
        stockMovementRepository.deleteAll();
        paymentEventRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        reservationRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        lotRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        pointCycleRepository.deleteAll();
        orderCycleRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        pickupPointRepository.deleteAll();
        commissionPolicyRepository.deleteAll();
        userRepository.deleteAll();

        adminUserId = createUser("admin@test.vn", "0910000001", "Admin").getId();
        warehouseUserId = createUser("wh@test.vn", "0910000002", "Thủ Kho").getId();
        driverUserId = createUser("driver@test.vn", "0910000003", "Tài Xế").getId();
        agentUserId = createUser("agent@test.vn", "0910000004", "Đại Lý").getId();
        customerUserId = createUser("cust@test.vn", "0910000005", "Khách Hàng").getId();

        PickupPointEntity point = new PickupPointEntity();
        point.setCode("PP-HN-BTL01");
        point.setName("Đại lý Minh Khai - Bắc Từ Liêm");
        point.setAddress("298 Cầu Diễn, Bắc Từ Liêm, Hà Nội");
        point.setLatitude(new BigDecimal("21.053750"));
        point.setLongitude(new BigDecimal("105.735120"));
        point.setColdStorageCapacity(150);
        point.setStatus("ACTIVE");
        pickupPointId = pickupPointRepository.save(point).getId();

        CommissionPolicyEntity policy = new CommissionPolicyEntity();
        policy.setPolicyCode("POL-5PCT");
        policy.setCalcType("PERCENTAGE_NET_MERCHANDISE");
        policy.setPercentageRate(new BigDecimal("5.00"));
        policy.setEffectiveFrom(ORDER_TIME_D.minusDays(30));
        commissionPolicyRepository.save(policy);

        OrderCycleEntity cycle = new OrderCycleEntity();
        cycle.setCycleCode("CYCLE-2026-11-18");
        cycle.setDeliveryDate(LocalDate.of(2026, 11, 18));
        cycle.setOpenAt(OffsetDateTime.parse("2026-11-17T00:00:00+07:00"));
        cycle.setCutoffAt(CUTOFF_TIME_D);
        cycle.setAutoConfirmAt(AUTO_CONFIRM_D1);
        cycle.setWarehousePackTargetAt(DISPATCH_D1_0800);
        cycle.setDispatchStartAt(DISPATCH_D1_0800);
        cycle.setPickupStartAt(PICKUP_START_D1_1600);
        cycle.setAutoCompleteAt(AUTO_COMPLETE_D2_0000);
        cycle.setPaymentTimeoutMinutes(30);
        cycle.setStatus("OPEN");
        cycleD1Id = orderCycleRepository.save(cycle).getId();

        PointCycleEntity pc = new PointCycleEntity();
        pc.setCycleId(cycleD1Id);
        pc.setPickupPointId(pickupPointId);
        pc.setMaxOrders(150);
        pc.setCurrentOrders(0);
        pc.setStatus("OPEN");
        pointCycleRepository.save(pc);

        ProductEntity product = new ProductEntity();
        product.setSkuPrefix("MEAT-PORK-FEFO");
        product.setName("Thịt ba chỉ heo VietGAP");
        product.setCategory("Thịt tươi sống");
        product.setStorageType("CHILLED");
        product.setMinShelfLifeDaysAtPickup(2); // Ngày nhận 18/11 -> yêu cầu hạn dùng >= 20/11/2026
        product.setIsActive(true);
        product = productRepository.save(product);

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProductId(product.getId());
        variant.setSku("MEAT-PORK-FEFO-500G");
        variant.setPackName("Khay 500g");
        variant.setPackWeightGrams(500);
        variant.setUnitPrice(MoneyUtils.of(100000));
        variant.setIsActive(true);
        variantId = productVariantRepository.save(variant).getId();

        InventoryBalanceEntity bal = new InventoryBalanceEntity();
        bal.setCycleId(cycleD1Id);
        bal.setVariantId(variantId);
        bal.setAvailableQty(50);
        bal.setReservedQty(0);
        bal.setCommittedQty(0);
        bal.setUpdatedAt(ORDER_TIME_D);
        inventoryBalanceRepository.save(bal);
    }

    @AfterEach
    void tearDown() {
        timeProvider.resetToSystemClock();
    }

    @Test
    @DisplayName("AT-07: Xuất kho theo FEFO với các lô có hạn sử dụng khác nhau, bỏ qua lô hết hạn/quá cận date và lô QUARANTINED")
    void at07_fefoAllocationSelectsEarliestValidLotAndSkipsQuarantinedOrTooCloseExpiry() {
        // Ngày nhận hàng D+1 là 2026-11-18, min_shelf_life_days_at_pickup = 2 -> Hạn sử dụng tối thiểu hợp lệ là 2026-11-20.
        // Lô 1: Hết hạn 19/11/2026 (không đủ 2 ngày sử dụng sau khi nhận) -> Phải bỏ qua
        createLot("LOT-TOO-CLOSE-1119", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 19), 10, "AVAILABLE");
        // Lô 2: Hết hạn 20/11/2026 nhưng bị khóa QUARANTINED -> Phải bỏ qua
        createLot("LOT-QUARANTINED-1120", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20), 10, "QUARANTINED");
        // Lô 3: Hết hạn 21/11/2026, AVAILABLE, còn 2 gói -> Phải được chọn đầu tiên (FEFO)
        LotEntity lotEarliestValid = createLot("LOT-VALID-EARLIEST-1121", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 21), 2, "AVAILABLE");
        // Lô 4: Hết hạn 24/11/2026, AVAILABLE, còn 10 gói -> Được chọn tiếp theo cho phần thiếu (1 gói)
        LotEntity lotLaterValid = createLot("LOT-VALID-LATER-1124", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 24), 10, "AVAILABLE");

        // Khách đặt 3 gói và thanh toán thành công
        Long orderId = createPaidAndConfirmedOrder(3);

        // Kho chạy phân bổ lô FEFO lúc 01:00 sáng ngày D+1 (18/11)
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T01:00:00+07:00"));
        List<OrderLotAllocationEntity> allocations = warehouseFefoService.allocateFefoForOrder(warehouseUserId, orderId);

        assertEquals(2, allocations.size(), "Phải phân bổ 2 gói từ lô 21/11 và 1 gói từ lô 24/11");
        assertEquals(lotEarliestValid.getId(), allocations.get(0).getLotId());
        assertEquals(2, allocations.get(0).getAllocatedQty());
        assertEquals(lotLaterValid.getId(), allocations.get(1).getLotId());
        assertEquals(1, allocations.get(1).getAllocatedQty());

        assertEquals(0, lotRepository.findById(lotEarliestValid.getId()).orElseThrow().getRemainingQty());
        assertEquals(9, lotRepository.findById(lotLaterValid.getId()).orElseThrow().getRemainingQty());
    }

    @Test
    @DisplayName("AT-08: Đại lý kiểm nhận phát hiện 1 kiện bị dập hỏng trong chuyến giao -> Đơn hỏng bị giữ ở ISSUE_HOLD, đơn nguyên vẹn khác mở READY_FOR_PICKUP")
    void at08_agentHandoverWithOneDamagedPackage_holdsBrokenOrderAndOpensIntactOrders() {
        createLot("LOT-FEFO-AT08", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23), 50, "AVAILABLE");

        // Đơn 1 (sẽ có 2 kiện: 1 nguyên vẹn, 1 bị dập hỏng) & Đơn 2 (nguyên vẹn 100%) cùng đại lý
        Long brokenOrderId = createPaidAndConfirmedOrder(2);
        Long intactOrderId = createPaidAndConfirmedOrder(1);

        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T06:00:00+07:00"));
        WarehouseFefoService.PackedOrderResult packedBroken = warehouseFefoService.packOrderIntoPackages(
                warehouseUserId, brokenOrderId, List.of("CHILLED", "AMBIENT")
        );
        WarehouseFefoService.PackedOrderResult packedIntact = warehouseFefoService.packOrderIntoPackages(
                warehouseUserId, intactOrderId, List.of("CHILLED")
        );

        FulfillmentService.TripCreatedResult tripResult = fulfillmentService.createDeliveryTrip(
                cycleD1Id, driverUserId, List.of(pickupPointId)
        );
        Long tripId = tripResult.trip().getId();
        Long tripStopId = tripResult.stops().get(0).getId();

        timeProvider.setFixedTime(DISPATCH_D1_0800);
        fulfillmentService.confirmWarehouseToDriverHandover(warehouseUserId, driverUserId, tripId, "Xuất kho đúng 08:00");

        // Tài xế giao tới đại lý lúc 15:30: Kiện 1 của Đơn 1 ACCEPTED, Kiện 2 của Đơn 1 DAMAGED; Kiện của Đơn 2 ACCEPTED
        timeProvider.setFixedTime(HANDOVER_D1_1530);
        Long brokenPkg1Id = packedBroken.packages().get(0).getId();
        Long brokenPkg2Id = packedBroken.packages().get(1).getId();
        Long intactPkgId = packedIntact.packages().get(0).getId();

        FulfillmentService.AgentHandoverOutcome outcome = fulfillmentService.confirmDriverToAgentHandover(
                driverUserId,
                agentUserId,
                tripStopId,
                List.of(
                        new FulfillmentService.PackageInspectionInput(brokenPkg1Id, "ACCEPTED", null),
                        new FulfillmentService.PackageInspectionInput(brokenPkg2Id, "DAMAGED", "https://img.o2ofood.vn/damaged-pkg2.jpg"),
                        new FulfillmentService.PackageInspectionInput(intactPkgId, "ACCEPTED", null)
                ),
                "Kiện 2 của đơn 1 bị móp khay"
        );

        assertEquals(List.of(intactOrderId), outcome.readyForPickupOrderIds());
        assertEquals(List.of(brokenOrderId), outcome.issueHoldOrderIds());

        OrderEntity brokenOrder = orderRepository.findById(brokenOrderId).orElseThrow();
        assertEquals(FulfillmentStatus.ISSUE_HOLD, brokenOrder.getFulfillmentStatus(),
                "Đơn có 1 kiện hỏng phải bị giữ ở ISSUE_HOLD");
        assertTrue(brokenOrder.getHasOpenIssue(), "has_open_issue phải được bật = true");

        OrderEntity intactOrder = orderRepository.findById(intactOrderId).orElseThrow();
        assertEquals(FulfillmentStatus.READY_FOR_PICKUP, intactOrder.getFulfillmentStatus(),
                "Đơn nguyên vẹn khác cùng đại lý vẫn mở READY_FOR_PICKUP bình thường");

        // Kiểm tra Outbox Events: Đơn hỏng không được phát sự kiện ORDER_READY_FOR_PICKUP
        List<OutboxEventEntity> brokenEvents = outboxEventRepository.findByAggregateIdOrderByIdAsc(String.valueOf(brokenOrderId));
        assertTrue(brokenEvents.stream().noneMatch(e -> "ORDER_READY_FOR_PICKUP".equals(e.getEventType())),
                "Tuyệt đối không gửi thông báo Sẵn sàng nhận hàng cho đơn bị hỏng kiện");

        List<OutboxEventEntity> intactEvents = outboxEventRepository.findByAggregateIdOrderByIdAsc(String.valueOf(intactOrderId));
        assertTrue(intactEvents.stream().anyMatch(e -> "ORDER_READY_FOR_PICKUP".equals(e.getEventType())),
                "Đơn nguyên vẹn phải được phát thông báo ORDER_READY_FOR_PICKUP");
    }

    @Test
    @DisplayName("AT-09: Khách bấm 'Đã nhận hàng' cùng lúc Job 00:00 D+2 tự hoàn tất -> Chỉ sinh đúng 1 dòng CREDIT_COMMISSION")
    void at09_customerConfirmConcurrentWithAutoCompleteJob_recordsExactlyOneCommissionEntry() {
        createLot("LOT-FEFO-AT09", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 25), 50, "AVAILABLE");
        Long orderId = createOrderDeliveredAndHandedOverAtAgent(2);

        // Lúc 00:00:00 Ngày D+2: Khách bấm "Đã nhận hàng" và Job AutoCompleteOrdersJob cùng chạy
        timeProvider.setFixedTime(AUTO_COMPLETE_D2_0000);
        fulfillmentService.confirmReceivedByCustomer(customerUserId, orderId);
        autoCompleteOrdersJob.completeEligibleOrders();

        // Thử gọi trực tiếp recordCreditCommissionOnce thêm lần nữa để kiểm chứng tính bất biến & chống trùng
        OrderEntity completedOrder = orderRepository.findById(orderId).orElseThrow();
        commissionService.recordCreditCommissionOnce(completedOrder, AUTO_COMPLETE_D2_0000);

        assertEquals(OrderStatus.COMPLETED, completedOrder.getOrderStatus());
        assertEquals(CompletionType.CUSTOMER_CONFIRMED, completedOrder.getCompletionType(),
                "Lưu đúng completion_type của giao dịch thắng (CUSTOMER_CONFIRMED)");

        List<CommissionLedgerEntity> ledgerEntries = commissionLedgerRepository.findByOrderIdOrderByIdAsc(orderId);
        assertEquals(1, ledgerEntries.size(), "Chỉ được sinh đúng 1 dòng CREDIT_COMMISSION duy nhất cho đơn hàng");
        assertEquals(CommissionEntryType.CREDIT_COMMISSION, ledgerEntries.get(0).getEntryType());
        assertEquals(new BigDecimal("10000.00"), ledgerEntries.get(0).getAmount()); // 5% của 200,000đ = 10,000đ
    }

    @Test
    @DisplayName("AT-10: Đơn đủ điều kiện lúc 00:00 D+2 nhưng khách chưa đến lấy -> COMPLETED (SYSTEM_TIMEOUT), physical_handed_over_at = NULL và is_unclaimed_at_agent = 1")
    void at10_autoCompleteWhenCustomerNotPickedUp_setsSystemTimeoutKeepsPhysicalHandedOverNullAndMarksUnclaimed() {
        createLot("LOT-FEFO-AT10", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 25), 50, "AVAILABLE");
        // Đơn đã được giao tới đại lý và kiểm nhận đủ (READY_FOR_PICKUP), nhưng khách chưa tới quét QR nhận hàng
        Long orderId = createOrderReadyAtAgentWithoutCustomerPickup(2);

        timeProvider.setFixedTime(AUTO_COMPLETE_D2_0000);
        int completed = autoCompleteOrdersJob.completeEligibleOrders();
        assertEquals(1, completed);

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());
        assertEquals(CompletionType.SYSTEM_TIMEOUT, order.getCompletionType());
        assertNull(order.getPhysicalHandedOverAt(),
                "Khi khách chưa tới lấy hàng, physical_handed_over_at bắt buộc phải để NULL");
        assertTrue(order.getIsUnclaimedAtAgent(),
                "Đơn phải nằm trong danh sách hàng tồn khách chưa lấy tại đại lý (is_unclaimed_at_agent = 1)");

        List<OrderEntity> unclaimedList = orderRepository.findByPickupPointIdAndIsUnclaimedAtAgentTrueOrderByIdAsc(pickupPointId);
        assertEquals(1, unclaimedList.size());
        assertEquals(orderId, unclaimedList.get(0).getId());

        List<CommissionLedgerEntity> ledgerEntries = commissionLedgerRepository.findByOrderIdOrderByIdAsc(orderId);
        assertEquals(1, ledgerEntries.size(), "Đại lý vẫn được ghi nhận hoa hồng đúng 1 lần khi đơn tự hoàn tất");
    }

    @Test
    @DisplayName("AT-11: Hoàn tiền cho đơn đã hoàn tất & đã ghi hoa hồng -> Hàng hoàn về kho cách ly (không cộng available_qty) + ghi 1 dòng DEBIT_REFUND_REVERSAL âm")
    void at11_refundForCompletedOrderWithCommission_quarantinesReturnedStockAndAppendsNegativeDebitReversal() {
        createLot("LOT-FEFO-AT11", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 25), 50, "AVAILABLE");
        Long orderId = createOrderDeliveredAndHandedOverAtAgent(2);

        // Đơn hoàn tất và đã ghi nhận hoa hồng +10,000đ cho đại lý
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T18:00:00+07:00"));
        fulfillmentService.confirmReceivedByCustomer(customerUserId, orderId);

        List<CommissionLedgerEntity> beforeRefundLedger = commissionLedgerRepository.findByOrderIdOrderByIdAsc(orderId);
        assertEquals(1, beforeRefundLedger.size());
        assertEquals(new BigDecimal("10000.00"), beforeRefundLedger.get(0).getAmount());

        InventoryBalanceEntity stockBeforeReturn = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        int availableBeforeReturn = stockBeforeReturn.getAvailableQty();

        // Khách báo sự cố hàng lỗi trong thời hạn khiếu nại
        ExceptionReturnService.ReportedIssueResult issueResult = exceptionReturnService.reportCustomerIssue(
                customerUserId,
                new ExceptionReturnService.ReportIssueRequest(
                        orderId,
                        "DAMAGED",
                        "Phát hiện khay thịt bị rách màng bọc bên trong",
                        "https://img.o2ofood.vn/claim-1.jpg"
                )
        );

        // Tài xế chở hàng hoàn về kho trung tâm -> Kho kiểm nhận vào khu cách ly (RETURN_TO_QUARANTINE)
        RefundEntity pendingRefund = exceptionReturnService.receiveReturnAtQuarantineWarehouse(
                warehouseUserId,
                driverUserId,
                issueResult.returnRequestId(),
                "Đã kiểm nhận 2 khay hoàn về khu cách ly hàng lỗi"
        );
        assertEquals("PENDING_APPROVAL", pendingRefund.getStatus());

        InventoryBalanceEntity stockAfterReturn = inventoryBalanceRepository
                .findByCycleIdAndVariantId(cycleD1Id, variantId).orElseThrow();
        assertEquals(availableBeforeReturn, stockAfterReturn.getAvailableQty(),
                "Tuyệt đối không được cộng hàng hoàn về lại available_qty thương mại");

        // Admin phê duyệt hoàn tiền -> Hoàn tất Refund và tự động ghi 1 dòng DEBIT_REFUND_REVERSAL âm vào commission_ledger
        RefundEntity completedRefund = exceptionReturnService.approveAndCompleteRefundByAdmin(adminUserId, pendingRefund.getId());
        assertEquals("COMPLETED", completedRefund.getStatus());

        List<CommissionLedgerEntity> afterRefundLedger = commissionLedgerRepository.findByOrderIdOrderByIdAsc(orderId);
        assertEquals(2, afterRefundLedger.size(), "Sổ cái bất biến phải giữ dòng CREDIT cũ và thêm 1 dòng DEBIT_REFUND_REVERSAL mới");
        assertEquals(CommissionEntryType.CREDIT_COMMISSION, afterRefundLedger.get(0).getEntryType());
        assertEquals(new BigDecimal("10000.00"), afterRefundLedger.get(0).getAmount());
        assertEquals(CommissionEntryType.DEBIT_REFUND_REVERSAL, afterRefundLedger.get(1).getEntryType());
        assertEquals(new BigDecimal("-10000.00"), afterRefundLedger.get(1).getAmount());

        BigDecimal netAgentBalance = commissionLedgerRepository.calculateNetCommissionBalance(pickupPointId);
        assertEquals(0, MoneyUtils.ZERO.compareTo(MoneyUtils.normalize(netAgentBalance)),
                "Số dư hoa hồng ròng của đại lý cho đơn hoàn tiền phải về 0.00");
    }

    private UserEntity createUser(String email, String phone, String fullName) {
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash("hash");
        u.setFullName(fullName);
        u.setStatus("ACTIVE");
        u.setCreatedAt(ORDER_TIME_D);
        return userRepository.save(u);
    }

    private LotEntity createLot(String code, LocalDate mfgDate, LocalDate expDate, int qty, String status) {
        LotEntity lot = new LotEntity();
        lot.setVariantId(variantId);
        lot.setLotCode(code);
        lot.setSupplierName("Nông trại CP");
        lot.setManufacturedDate(mfgDate);
        lot.setExpiryDate(expDate);
        lot.setInitialQty(qty);
        lot.setRemainingQty(qty);
        lot.setStatus(status);
        lot.setReceivedAt(ORDER_TIME_D.minusDays(1));
        return lotRepository.save(lot);
    }

    private Long createPaidAndConfirmedOrder(int quantity) {
        timeProvider.setFixedTime(ORDER_TIME_D);
        OrderService.CheckoutResponse checkout = orderService.checkout(
                customerUserId,
                new OrderService.CheckoutRequest(pickupPointId, cycleD1Id,
                        List.of(new OrderService.CheckoutItemRequest(variantId, quantity))),
                "127.0.0.1"
        );
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "DEMO_O2OFOOD");
        params.put("vnp_Amount", String.valueOf(checkout.totalAmount().multiply(BigDecimal.valueOf(100)).longValue()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", checkout.txnRef());
        params.put("vnp_OrderInfo", "Thanh toan " + checkout.txnRef());
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "99887766");
        params.put("vnp_PayDate", "20261117140500");
        params.put("vnp_SecureHash", vnPaySigner.signParams(params));
        vnPayService.processIpnWebhook(params);

        timeProvider.setFixedTime(AUTO_CONFIRM_D1);
        autoConfirmOrdersJob.confirmEligibleOrders();
        return checkout.orderId();
    }

    private Long createOrderReadyAtAgentWithoutCustomerPickup(int quantity) {
        Long orderId = createPaidAndConfirmedOrder(quantity);
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T06:00:00+07:00"));
        WarehouseFefoService.PackedOrderResult packed = warehouseFefoService.packOrderIntoPackages(
                warehouseUserId, orderId, List.of("CHILLED")
        );
        FulfillmentService.TripCreatedResult tripResult = fulfillmentService.createDeliveryTrip(
                cycleD1Id, driverUserId, List.of(pickupPointId)
        );
        timeProvider.setFixedTime(DISPATCH_D1_0800);
        fulfillmentService.confirmWarehouseToDriverHandover(
                warehouseUserId, driverUserId, tripResult.trip().getId(), "Xuất kho"
        );
        timeProvider.setFixedTime(HANDOVER_D1_1530);
        fulfillmentService.confirmDriverToAgentHandover(
                driverUserId,
                agentUserId,
                tripResult.stops().get(0).getId(),
                List.of(new FulfillmentService.PackageInspectionInput(packed.packages().get(0).getId(), "ACCEPTED", null)),
                "Đại lý nhận đủ"
        );
        return orderId;
    }

    private Long createOrderDeliveredAndHandedOverAtAgent(int quantity) {
        Long orderId = createPaidAndConfirmedOrder(quantity);
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T06:00:00+07:00"));
        WarehouseFefoService.PackedOrderResult packed = warehouseFefoService.packOrderIntoPackages(
                warehouseUserId, orderId, List.of("CHILLED")
        );
        FulfillmentService.TripCreatedResult tripResult = fulfillmentService.createDeliveryTrip(
                cycleD1Id, driverUserId, List.of(pickupPointId)
        );
        timeProvider.setFixedTime(DISPATCH_D1_0800);
        fulfillmentService.confirmWarehouseToDriverHandover(
                warehouseUserId, driverUserId, tripResult.trip().getId(), "Xuất kho"
        );
        timeProvider.setFixedTime(HANDOVER_D1_1530);
        fulfillmentService.confirmDriverToAgentHandover(
                driverUserId,
                agentUserId,
                tripResult.stops().get(0).getId(),
                List.of(new FulfillmentService.PackageInspectionInput(packed.packages().get(0).getId(), "ACCEPTED", null)),
                "Đại lý nhận đủ"
        );
        timeProvider.setFixedTime(OffsetDateTime.parse("2026-11-18T17:00:00+07:00"));
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setPhysicalHandedOverAt(timeProvider.now());
        order.setPickupQrUsedAt(timeProvider.now());
        order.setFulfillmentStatus(FulfillmentStatus.HANDED_OVER);
        orderRepository.saveAndFlush(order);
        return orderId;
    }
}
