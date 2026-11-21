package vn.haui.o2ofood.modules.ordering;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.security.UserPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final TimeProvider timeProvider;

    public OrderController(OrderService orderService, TimeProvider timeProvider) {
        this.orderService = orderService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderService.CheckoutResponse>> checkout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ValidatedCheckoutRequest request,
            HttpServletRequest servletRequest) {
        List<OrderService.CheckoutItemRequest> items = request.items().stream()
                .map(i -> new OrderService.CheckoutItemRequest(i.variantId(), i.quantity()))
                .toList();
        OrderService.CheckoutResponse response = orderService.checkout(
                principal.getId(),
                new OrderService.CheckoutRequest(request.pickupPointId(), request.cycleId(), items),
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.ok("Giữ tồn kho và khởi tạo thanh toán VNPAY thành công", response, timeProvider.now()));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderService.CancelOrderResponse>> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @RequestBody(required = false) CancelRequest body) {
        String reason = body != null ? body.reason() : "CUSTOMER_SELF_CANCEL";
        OrderService.CancelOrderResponse response = orderService.cancelOrderByCustomer(principal.getId(), orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công", response, timeProvider.now()));
    }

    @PostMapping("/{orderId}/late-payment-resolution")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderService.OrderSummaryDto>> resolveLatePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @RequestBody OrderService.LatePaymentResolutionRequest request) {
        OrderService.OrderSummaryDto response = orderService.resolveLatePayment(principal.getId(), orderId, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận lựa chọn xử lý thanh toán muộn", response, timeProvider.now()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<List<OrderService.OrderSummaryDto>>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getCustomerOrders(principal.getId()), timeProvider.now()));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderService.OrderDetailDto>> getOrderDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderDetailForCustomer(principal.getId(), orderId), timeProvider.now()));
    }

    public record ValidatedCheckoutItem(
            @NotNull(message = "variantId không được để trống")
            Long variantId,
            @Min(value = 1, message = "Số lượng gói tối thiểu là 1")
            int quantity
    ) {
    }

    public record ValidatedCheckoutRequest(
            @NotNull(message = "pickupPointId không được để trống")
            Long pickupPointId,
            @NotNull(message = "cycleId không được để trống")
            Long cycleId,
            @NotEmpty(message = "Giỏ hàng không được để trống")
            List<@Valid ValidatedCheckoutItem> items
    ) {
    }

    public record CancelRequest(
            String reason
    ) {
    }
}
