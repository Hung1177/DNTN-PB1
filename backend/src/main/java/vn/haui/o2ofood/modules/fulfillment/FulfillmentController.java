package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.commission.CommissionService;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderRepository;
import vn.haui.o2ofood.security.OwnershipSecurityService;
import vn.haui.o2ofood.security.UserPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fulfillment")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;
    private final CommissionService commissionService;
    private final OrderRepository orderRepository;
    private final OwnershipSecurityService ownershipSecurityService;
    private final TimeProvider timeProvider;

    public FulfillmentController(FulfillmentService fulfillmentService,
                                 CommissionService commissionService,
                                 OrderRepository orderRepository,
                                 OwnershipSecurityService ownershipSecurityService,
                                 TimeProvider timeProvider) {
        this.fulfillmentService = fulfillmentService;
        this.commissionService = commissionService;
        this.orderRepository = orderRepository;
        this.ownershipSecurityService = ownershipSecurityService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/trips")
    @PreAuthorize("hasAnyAuthority('ROLE_DISPATCHER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentService.TripCreatedResult>> createTrip(
            @RequestBody CreateTripRequest request) {
        FulfillmentService.TripCreatedResult result = fulfillmentService.createDeliveryTrip(
                request.cycleId(), request.driverId(), request.pickupPointIds()
        );
        return ResponseEntity.ok(ApiResponse.ok("Tạo chuyến giao hàng thành công", result, timeProvider.now()));
    }

    @PostMapping("/trips/{tripId}/warehouse-handover")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_DRIVER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<HandoverRecordEntity>> confirmWarehouseHandover(
            @PathVariable("tripId") Long tripId,
            @RequestBody WarehouseHandoverRequest request) {
        HandoverRecordEntity record = fulfillmentService.confirmWarehouseToDriverHandover(
                request.warehouseUserId(), request.driverId(), tripId, request.note()
        );
        return ResponseEntity.ok(ApiResponse.ok("Bàn giao Kho -> Tài xế thành công", record, timeProvider.now()));
    }

    @PostMapping("/trip-stops/{tripStopId}/agent-handover")
    @PreAuthorize("hasAnyAuthority('ROLE_DRIVER', 'ROLE_AGENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FulfillmentService.AgentHandoverOutcome>> confirmAgentHandover(
            @PathVariable("tripStopId") Long tripStopId,
            @RequestBody AgentHandoverRequest request) {
        FulfillmentService.AgentHandoverOutcome outcome = fulfillmentService.confirmDriverToAgentHandover(
                request.driverId(),
                request.agentUserId(),
                tripStopId,
                request.inspections(),
                request.note()
        );
        return ResponseEntity.ok(ApiResponse.ok("Bàn giao Tài xế -> Đại lý thành công", outcome, timeProvider.now()));
    }

    @PostMapping("/orders/{orderId}/agent-scan-qr")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<OrderEntity>> scanCustomerQrByAgent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @RequestBody ScanQrRequest request) {
        OrderEntity order = fulfillmentService.scanCustomerPickupQrByAgent(principal.getId(), orderId, request.qrToken());
        return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận bàn giao vật lý cho khách hàng", order, timeProvider.now()));
    }

    @PostMapping("/orders/{orderId}/customer-confirm-received")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderEntity>> confirmReceivedByCustomer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId) {
        OrderEntity order = fulfillmentService.confirmReceivedByCustomer(principal.getId(), orderId);
        return ResponseEntity.ok(ApiResponse.ok("Khách hàng đã xác nhận nhận hàng thành công", order, timeProvider.now()));
    }

    @GetMapping("/agent/pickup-points/{pickupPointId}/unclaimed-orders")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_DISPATCHER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderEntity>>> getUnclaimedOrdersAtAgent(
            @PathVariable("pickupPointId") Long pickupPointId) {
        if (!ownershipSecurityService.isAssignedToPickupPoint(pickupPointId)) {
            throw new vn.haui.o2ofood.common.BusinessException(vn.haui.o2ofood.common.ErrorCode.UNAUTHORIZED_ACCESS);
        }
        List<OrderEntity> unclaimed = orderRepository.findByPickupPointIdAndIsUnclaimedAtAgentTrueOrderByIdAsc(pickupPointId);
        return ResponseEntity.ok(ApiResponse.ok(unclaimed, timeProvider.now()));
    }

    @GetMapping("/agent/pickup-points/{pickupPointId}/commission-ledger")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionService.AgentCommissionStatementDto>> getAgentCommissionLedger(
            @PathVariable("pickupPointId") Long pickupPointId) {
        return ResponseEntity.ok(ApiResponse.ok(
                commissionService.getStatementForPickupPoint(pickupPointId),
                timeProvider.now()
        ));
    }

    public record CreateTripRequest(
            Long cycleId,
            Long driverId,
            List<Long> pickupPointIds
    ) {
    }

    public record WarehouseHandoverRequest(
            Long warehouseUserId,
            Long driverId,
            String note
    ) {
    }

    public record AgentHandoverRequest(
            Long driverId,
            Long agentUserId,
            List<FulfillmentService.PackageInspectionInput> inspections,
            String note
    ) {
    }

    public record ScanQrRequest(
            String qrToken
    ) {
    }
}
