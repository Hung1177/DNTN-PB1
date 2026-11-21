package vn.haui.o2ofood.modules.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final VnPayService vnPayService;
    private final TimeProvider timeProvider;

    public PaymentController(VnPayService vnPayService, TimeProvider timeProvider) {
        this.vnPayService = vnPayService;
        this.timeProvider = timeProvider;
    }

    /**
     * UC-C03: Webhook IPN do VNPAY gọi sang (hỗ trợ cả POST và GET theo chuẩn VNPAY Sandbox).
     */
    @RequestMapping(value = "/vnpay-ipn", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<VnPayService.VnPayIpnResponse> handleVnPayIpn(
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, String> bodyParams) {
        Map<String, String> merged = (bodyParams != null && !bodyParams.isEmpty()) ? bodyParams : queryParams;
        VnPayService.VnPayIpnResponse response = vnPayService.processIpnWebhook(merged);
        return ResponseEntity.ok(response);
    }

    /**
     * UC-C03: Return URL khi trình duyệt/Android chuyển hướng về từ VNPAY.
     */
    @GetMapping("/vnpay-return")
    public ResponseEntity<ApiResponse<VnPayService.ReturnUrlStatusDto>> handleVnPayReturn(
            @RequestParam Map<String, String> queryParams) {
        VnPayService.ReturnUrlStatusDto dto = vnPayService.handleReturnUrl(queryParams);
        return ResponseEntity.ok(ApiResponse.ok(dto, timeProvider.now()));
    }
}
