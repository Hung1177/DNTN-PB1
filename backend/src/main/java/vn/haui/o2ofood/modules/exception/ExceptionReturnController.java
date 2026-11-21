package vn.haui.o2ofood.modules.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.payment.RefundEntity;
import vn.haui.o2ofood.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/exceptions")
public class ExceptionReturnController {

    private final ExceptionReturnService exceptionReturnService;
    private final TimeProvider timeProvider;

    public ExceptionReturnController(ExceptionReturnService exceptionReturnService,
                                     TimeProvider timeProvider) {
        this.exceptionReturnService = exceptionReturnService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/issues")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<ExceptionReturnService.ReportedIssueResult>> reportIssue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ExceptionReturnService.ReportIssueRequest request) {
        ExceptionReturnService.ReportedIssueResult result =
                exceptionReturnService.reportCustomerIssue(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận báo cáo sự cố và khóa tự động hoàn tất đơn", result, timeProvider.now()));
    }

    @PostMapping("/returns/{returnRequestId}/warehouse-receive")
    @PreAuthorize("hasAnyAuthority('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<RefundEntity>> receiveReturnAtWarehouse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("returnRequestId") Long returnRequestId,
            @RequestBody WarehouseReceiveReturnRequest request) {
        RefundEntity refund = exceptionReturnService.receiveReturnAtQuarantineWarehouse(
                principal.getId(),
                request.driverUserId(),
                returnRequestId,
                request.inspectionNote()
        );
        return ResponseEntity.ok(ApiResponse.ok("Đã nhận hàng hoàn vào khu cách ly và mở khóa duyệt hoàn tiền", refund, timeProvider.now()));
    }

    @PostMapping("/refunds/{refundId}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<RefundEntity>> approveRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("refundId") Long refundId) {
        RefundEntity refund = exceptionReturnService.approveAndCompleteRefundByAdmin(principal.getId(), refundId);
        return ResponseEntity.ok(ApiResponse.ok("Đã phê duyệt hoàn tiền và điều chỉnh sổ cái hoa hồng", refund, timeProvider.now()));
    }

    public record WarehouseReceiveReturnRequest(
            Long driverUserId,
            String inspectionNote
    ) {
    }
}
