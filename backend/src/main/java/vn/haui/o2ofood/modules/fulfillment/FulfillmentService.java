package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.catalog.WarehouseFefoService;
import vn.haui.o2ofood.modules.commission.CommissionService;
import vn.haui.o2ofood.modules.exception.IssueEntity;
import vn.haui.o2ofood.modules.exception.IssueRepository;
import vn.haui.o2ofood.modules.exception.ReturnRequestEntity;
import vn.haui.o2ofood.modules.exception.ReturnRequestRepository;
import vn.haui.o2ofood.modules.exception.ReturnStatus;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventStatus;
import vn.haui.o2ofood.modules.ordering.CompletionType;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderRepository;
import vn.haui.o2ofood.modules.ordering.OrderStatus;
import vn.haui.o2ofood.security.OwnershipSecurityService;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class FulfillmentService {

    private static final AtomicLong TRIP_SEQ = new AtomicLong(100);

    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final PackageRepository packageRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final HandoverItemRepository handoverItemRepository;
    private final OrderRepository orderRepository;
    private final IssueRepository issueRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CommissionService commissionService;
    private final OwnershipSecurityService ownershipSecurityService;
    private final TimeProvider timeProvider;

    public FulfillmentService(TripRepository tripRepository,
                              TripStopRepository tripStopRepository,
                              PackageRepository packageRepository,
                              HandoverRecordRepository handoverRecordRepository,
                              HandoverItemRepository handoverItemRepository,
                              OrderRepository orderRepository,
                              IssueRepository issueRepository,
                              ReturnRequestRepository returnRequestRepository,
                              OutboxEventRepository outboxEventRepository,
                              CommissionService commissionService,
                              OwnershipSecurityService ownershipSecurityService,
                              TimeProvider timeProvider) {
        this.tripRepository = tripRepository;
        this.tripStopRepository = tripStopRepository;
        this.packageRepository = packageRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.orderRepository = orderRepository;
        this.issueRepository = issueRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.commissionService = commissionService;
        this.ownershipSecurityService = ownershipSecurityService;
        this.timeProvider = timeProvider;
    }

    /**
     * UC-D01: Điều phối tạo chuyến giao hàng (Trips & TripStops) và gán kiện hàng theo đại lý.
     */
    @Transactional
    public TripCreatedResult createDeliveryTrip(Long cycleId, Long driverId, List<Long> pickupPointIdsInOrder) {
        OffsetDateTime now = timeProvider.now();
        String tripCode = "TRIP-" + cycleId + "-" + TRIP_SEQ.incrementAndGet();

        TripEntity trip = new TripEntity();
        trip.setTripCode(tripCode);
        trip.setCycleId(cycleId);
        trip.setDriverId(driverId);
        trip.setTripType("DELIVERY");
        trip.setStatus("PLANNED");
        trip = tripRepository.save(trip);

        List<OrderEntity> cycleOrders = orderRepository.findConfirmedOrdersForWarehousePacking(cycleId);
        Map<Long, List<OrderEntity>> ordersByPoint = cycleOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getPickupPointId));

        List<TripStopEntity> stops = new ArrayList<>();
        int seq = 1;
        for (Long pointId : pickupPointIdsInOrder) {
            TripStopEntity stop = new TripStopEntity();
            stop.setTripId(trip.getId());
            stop.setPickupPointId(pointId);
            stop.setStopSequence(seq++);
            stop.setEtaAt(now.plusHours(2));
            stop.setStatus("PENDING");
            stop = tripStopRepository.save(stop);
            stops.add(stop);

            List<OrderEntity> pointOrders = ordersByPoint.getOrDefault(pointId, List.of());
            for (OrderEntity order : pointOrders) {
                List<PackageEntity> pkgs = packageRepository.findByOrderIdOrderByPackageSeqAsc(order.getId());
                for (PackageEntity pkg : pkgs) {
                    pkg.setTripStopId(stop.getId());
                    packageRepository.save(pkg);
                }
            }
        }

        return new TripCreatedResult(trip, stops);
    }

    /**
     * UC-W03: Bàn giao kiện hàng từ Kho trung tâm cho Tài xế (Từ 08:00 Ngày D+1).
     */
    @Transactional
    public HandoverRecordEntity confirmWarehouseToDriverHandover(Long warehouseUserId,
                                                                 Long driverId,
                                                                 Long tripId,
                                                                 String note) {
        OffsetDateTime now = timeProvider.now();
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến xe"));
        if (!trip.getDriverId().equals(driverId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Chuyến xe không thuộc tài xế này");
        }

        List<TripStopEntity> stops = tripStopRepository.findByTripIdOrderByStopSequenceAsc(tripId);
        List<PackageEntity> allTripPackages = new ArrayList<>();
        Set<Long> orderIds = new HashSet<>();

        for (TripStopEntity stop : stops) {
            List<PackageEntity> stopPackages = packageRepository.findByTripStopIdOrderByIdAsc(stop.getId());
            for (PackageEntity pkg : stopPackages) {
                pkg.setStatus("IN_TRANSIT");
                packageRepository.save(pkg);
                allTripPackages.add(pkg);
                orderIds.add(pkg.getOrderId());
            }
        }

        for (Long orderId : orderIds) {
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setFulfillmentStatus(FulfillmentStatus.IN_TRANSIT);
                order.setUpdatedAt(now);
                orderRepository.save(order);
            });
        }

        trip.setStatus("IN_TRANSIT");
        trip.setDepartedAt(now);
        tripRepository.save(trip);

        HandoverRecordEntity record = new HandoverRecordEntity();
        record.setHandoverType("WAREHOUSE_TO_DRIVER");
        record.setFromUserId(warehouseUserId);
        record.setToUserId(driverId);
        record.setTotalExpectedPackages(allTripPackages.size());
        record.setAcceptedPackages(allTripPackages.size());
        record.setMissingPackages(0);
        record.setDamagedPackages(0);
        record.setNote(note);
        record.setSignedAt(now);
        record = handoverRecordRepository.save(record);

        for (PackageEntity pkg : allTripPackages) {
            HandoverItemEntity item = new HandoverItemEntity();
            item.setHandoverRecordId(record.getId());
            item.setPackageId(pkg.getId());
            item.setConditionStatus("ACCEPTED");
            handoverItemRepository.save(item);
        }

        return record;
    }

    /**
     * UC-DR01 & AT-08: Giao hàng đến Đại lý & Lập biên bản bàn giao (DRIVER_TO_AGENT).
     * - Chỉ những đơn nhận đủ 100% số kiện nguyên vẹn (ACCEPTED) mới chuyển sang READY_FOR_PICKUP và bắn thông báo cho khách.
     * - Nếu đơn có bất kỳ kiện nào bị DAMAGED hoặc MISSING -> giữ toàn bộ đơn ở ISSUE_HOLD,
     *   gắn has_open_issue = true, tự động sinh Issues & ReturnRequests, tuyệt đối không gửi thông báo "Sẵn sàng nhận hàng".
     */
    @Transactional
    public AgentHandoverOutcome confirmDriverToAgentHandover(Long driverId,
                                                             Long agentUserId,
                                                             Long tripStopId,
                                                             List<PackageInspectionInput> inspections,
                                                             String note) {
        OffsetDateTime now = timeProvider.now();
        TripStopEntity stop = tripStopRepository.findById(tripStopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy điểm dừng giao hàng"));
        TripEntity trip = tripRepository.findById(stop.getTripId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến giao hàng"));

        if (!trip.getDriverId().equals(driverId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Tài xế không được phân công chuyến giao hàng này");
        }

        Map<Long, PackageInspectionInput> inspectionMap = inspections.stream()
                .collect(Collectors.toMap(PackageInspectionInput::packageId, i -> i));

        List<PackageEntity> stopPackages = packageRepository.findByTripStopIdOrderByIdAsc(tripStopId);
        int acceptedCount = 0;
        int missingCount = 0;
        int damagedCount = 0;

        for (PackageEntity pkg : stopPackages) {
            PackageInspectionInput input = inspectionMap.get(pkg.getId());
            String condition = input != null ? input.conditionStatus() : "ACCEPTED";
            if ("ACCEPTED".equalsIgnoreCase(condition)) {
                acceptedCount++;
                pkg.setStatus("DELIVERED_TO_AGENT");
            } else if ("MISSING".equalsIgnoreCase(condition)) {
                missingCount++;
                pkg.setStatus("MISSING");
            } else if ("DAMAGED".equalsIgnoreCase(condition)) {
                damagedCount++;
                pkg.setStatus("DAMAGED");
            } else {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái kiểm nhận kiện không hợp lệ: " + condition);
            }
            packageRepository.save(pkg);
        }

        HandoverRecordEntity record = new HandoverRecordEntity();
        record.setHandoverType("DRIVER_TO_AGENT");
        record.setTripStopId(stop.getId());
        record.setFromUserId(driverId);
        record.setToUserId(agentUserId);
        record.setTotalExpectedPackages(stopPackages.size());
        record.setAcceptedPackages(acceptedCount);
        record.setMissingPackages(missingCount);
        record.setDamagedPackages(damagedCount);
        record.setNote(note);
        record.setSignedAt(now);
        record = handoverRecordRepository.save(record);

        for (PackageEntity pkg : stopPackages) {
            PackageInspectionInput input = inspectionMap.get(pkg.getId());
            HandoverItemEntity item = new HandoverItemEntity();
            item.setHandoverRecordId(record.getId());
            item.setPackageId(pkg.getId());
            item.setConditionStatus(input != null ? input.conditionStatus() : "ACCEPTED");
            item.setEvidenceImageUrl(input != null ? input.evidenceImageUrl() : null);
            handoverItemRepository.save(item);
        }

        stop.setArrivedAt(stop.getArrivedAt() != null ? stop.getArrivedAt() : now);
        stop.setHandoverCompletedAt(now);
        stop.setStatus("COMPLETED");
        tripStopRepository.save(stop);

        // Nhóm các kiện theo từng đơn hàng để đánh giá điều kiện mở nhận hàng (AT-08)
        Map<Long, List<PackageEntity>> packagesByOrder = stopPackages.stream()
                .collect(Collectors.groupingBy(PackageEntity::getOrderId));

        List<Long> readyOrderIds = new ArrayList<>();
        List<Long> heldOrderIds = new ArrayList<>();

        for (Map.Entry<Long, List<PackageEntity>> entry : packagesByOrder.entrySet()) {
            Long orderId = entry.getKey();
            OrderEntity order = orderRepository.findById(orderId).orElseThrow();
            List<PackageEntity> allOrderPackages = packageRepository.findByOrderIdOrderByPackageSeqAsc(orderId);

            boolean allDeliveredIntact = !allOrderPackages.isEmpty() && allOrderPackages.stream()
                    .allMatch(p -> "DELIVERED_TO_AGENT".equalsIgnoreCase(p.getStatus()));

            if (allDeliveredIntact) {
                order.setFulfillmentStatus(FulfillmentStatus.READY_FOR_PICKUP);
                order.setUpdatedAt(now);
                orderRepository.save(order);
                readyOrderIds.add(order.getId());

                emitOutboxEvent(
                        order.getId(),
                        "ORDER_READY_FOR_PICKUP",
                        "{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\",\"status\":\"READY_FOR_PICKUP\"}",
                        now
                );
            } else {
                // AT-08: Có ít nhất 1 kiện bị hỏng hoặc thiếu -> Giữ toàn bộ đơn ở ISSUE_HOLD, chặn bắn thông báo READY_FOR_PICKUP
                order.setFulfillmentStatus(FulfillmentStatus.ISSUE_HOLD);
                order.setHasOpenIssue(true);
                order.setUpdatedAt(now);
                orderRepository.save(order);
                heldOrderIds.add(order.getId());

                boolean hasDamaged = allOrderPackages.stream().anyMatch(p -> "DAMAGED".equalsIgnoreCase(p.getStatus()));
                IssueEntity issue = new IssueEntity();
                issue.setOrderId(order.getId());
                issue.setReportedByUserId(agentUserId);
                issue.setReporterRole("ROLE_AGENT");
                issue.setIssueType(hasDamaged ? "DAMAGED" : "MISSING_ITEM");
                issue.setDescription("Phát hiện kiện hàng lỗi/thiếu trong biên bản bàn giao tại đại lý");
                issue.setStatus("OPEN");
                issue.setCreatedAt(now);
                issue = issueRepository.save(issue);

                ReturnRequestEntity returnReq = new ReturnRequestEntity();
                returnReq.setIssueId(issue.getId());
                returnReq.setOrderId(order.getId());
                returnReq.setPickupPointId(stop.getPickupPointId());
                returnReq.setStatus(ReturnStatus.APPROVED_FOR_PICKUP);
                returnRequestRepository.save(returnReq);

                emitOutboxEvent(
                        order.getId(),
                        "ORDER_ISSUE_AT_AGENT_HANDOVER",
                        "{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\",\"status\":\"ISSUE_HOLD\"}",
                        now
                );
            }
        }

        return new AgentHandoverOutcome(record.getId(), readyOrderIds, heldOrderIds);
    }

    /**
     * UC-C05: Đại lý quét mã QR nhận hàng dùng 1 lần của khách hàng để bàn giao vật lý.
     */
    @Transactional
    public OrderEntity scanCustomerPickupQrByAgent(Long agentUserId, Long orderId, String rawQrToken) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!ownershipSecurityService.isAssignedToPickupPoint(order.getPickupPointId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Đại lý không thuộc điểm nhận của đơn hàng này");
        }
        if (order.getFulfillmentStatus() != FulfillmentStatus.READY_FOR_PICKUP) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Đơn hàng chưa ở trạng thái sẵn sàng nhận tại đại lý");
        }
        if (order.getPickupQrUsedAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Mã QR nhận hàng này đã được sử dụng trước đó");
        }

        String providedHash = WarehouseFefoService.sha256Hex(rawQrToken != null ? rawQrToken : "");
        if (order.getPickupQrTokenHash() == null || !order.getPickupQrTokenHash().equalsIgnoreCase(providedHash)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Mã QR nhận hàng không hợp lệ");
        }

        order.setPhysicalHandedOverAt(now);
        order.setPickupQrUsedAt(now);
        order.setFulfillmentStatus(FulfillmentStatus.HANDED_OVER);
        order.setIsUnclaimedAtAgent(false);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        List<PackageEntity> pkgs = packageRepository.findByOrderIdOrderByPackageSeqAsc(order.getId());
        for (PackageEntity pkg : pkgs) {
            pkg.setStatus("HANDED_TO_CUSTOMER");
            packageRepository.save(pkg);
        }

        return order;
    }

    /**
     * UC-C05 & AT-09: Khách hàng nhấn nút "Đã nhận hàng" (CUSTOMER_CONFIRMED).
     * - Dùng @Version khóa lạc quan chống đua dữ liệu với Job tự hoàn tất lúc 00:00 D+2.
     * - Nếu đại lý chưa quét bàn giao (PhysicalHandedOverAt == null), giữ nguyên PhysicalHandedOverAt = null
     *   và phát cảnh báo AuditAlert, tuyệt đối không giả mạo dấu thời gian bàn giao của đại lý.
     * - Ghi nhận hoa hồng đúng 1 lần vào CommissionLedger.
     */
    @Transactional
    public OrderEntity confirmReceivedByCustomer(Long customerId, Long orderId) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        ownershipSecurityService.assertCustomerOwnsOrder(customerId, order);

        if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            return order;
        }
        if (order.getFulfillmentStatus() != FulfillmentStatus.READY_FOR_PICKUP
                && order.getFulfillmentStatus() != FulfillmentStatus.HANDED_OVER) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Đơn hàng chưa sẵn sàng tại điểm nhận");
        }

        order.setCustomerConfirmedAt(now);
        order.setCompletedAt(now);
        order.setCompletionType(CompletionType.CUSTOMER_CONFIRMED);
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setUpdatedAt(now);

        // Flush ngay lập tức để kích hoạt kiểm tra @Version khi đua dữ liệu với Job 00:00 D+2 (AT-09)
        order = orderRepository.saveAndFlush(order);

        if (order.getPhysicalHandedOverAt() == null) {
            emitOutboxEvent(
                    order.getId(),
                    "AUDIT_ALERT_CUSTOMER_CONFIRMED_WITHOUT_AGENT_SCAN",
                    "{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\"}",
                    now
            );
        }

        commissionService.recordCreditCommissionOnce(order, now);

        return order;
    }

    private void emitOutboxEvent(Long orderId, String eventType, String jsonPayload, OffsetDateTime now) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(String.valueOf(orderId));
        outbox.setEventType(eventType);
        outbox.setPayload(jsonPayload);
        outbox.setStatus(OutboxEventStatus.PENDING);
        outbox.setCreatedAt(now);
        outboxEventRepository.save(outbox);
    }

    public record TripCreatedResult(
            TripEntity trip,
            List<TripStopEntity> stops
    ) {
    }

    public record PackageInspectionInput(
            Long packageId,
            String conditionStatus,
            String evidenceImageUrl
    ) {
    }

    public record AgentHandoverOutcome(
            Long handoverRecordId,
            List<Long> readyForPickupOrderIds,
            List<Long> issueHoldOrderIds
    ) {
    }
}
