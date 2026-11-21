package vn.haui.o2ofood.modules.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.ordering.OrderLotAllocationEntity;
import vn.haui.o2ofood.security.UserPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

    private final WarehouseFefoService warehouseFefoService;
    private final TimeProvider timeProvider;

    public WarehouseController(WarehouseFefoService warehouseFefoService, TimeProvider timeProvider) {
        this.warehouseFefoService = warehouseFefoService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/lots")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<LotEntity>> inboundLot(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody WarehouseFefoService.InboundLotRequest request) {
        LotEntity lot = warehouseFefoService.inboundLot(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Nhập lô mới thành công", lot, timeProvider.now()));
    }

    @PostMapping("/lots/{lotId}/quarantine")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<LotEntity>> quarantineLot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("lotId") Long lotId) {
        LotEntity lot = warehouseFefoService.quarantineLot(principal.getId(), lotId);
        return ResponseEntity.ok(ApiResponse.ok("Đã khóa cách ly lô hàng", lot, timeProvider.now()));
    }

    @PostMapping("/orders/{orderId}/allocate-fefo")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderLotAllocationEntity>>> allocateFefo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId) {
        List<OrderLotAllocationEntity> allocations = warehouseFefoService.allocateFefoForOrder(principal.getId(), orderId);
        return ResponseEntity.ok(ApiResponse.ok("Phân bổ lô FEFO thành công", allocations, timeProvider.now()));
    }

    @PostMapping("/orders/{orderId}/pack")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseFefoService.PackedOrderResult>> packOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @RequestBody(required = false) List<String> storageTypes) {
        WarehouseFefoService.PackedOrderResult result = warehouseFefoService.packOrderIntoPackages(
                principal.getId(), orderId, storageTypes
        );
        return ResponseEntity.ok(ApiResponse.ok("Đóng gói kiện hàng và sinh mã QR thành công", result, timeProvider.now()));
    }
}
