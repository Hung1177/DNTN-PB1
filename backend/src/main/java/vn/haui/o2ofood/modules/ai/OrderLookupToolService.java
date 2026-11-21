package vn.haui.o2ofood.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderRepository;
import vn.haui.o2ofood.modules.ordering.OrderService;
import vn.haui.o2ofood.security.OwnershipSecurityService;

import java.util.List;

/**
 * UC-C07 & AT-12: Dịch vụ Tool Calling / Function Calling tra cứu đơn hàng an toàn.
 * - Mọi truy vấn đơn hàng qua AI Chatbot bắt buộc kiểm tra authenticatedUserId từ JWT.
 * - Nếu khách hỏi đơn của người khác -> Từ chối ngay tại tầng Backend (403 UNAUTHORIZED_ACCESS).
 */
@Service
public class OrderLookupToolService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OwnershipSecurityService ownershipSecurityService;

    public OrderLookupToolService(OrderRepository orderRepository,
                                  OrderService orderService,
                                  OwnershipSecurityService ownershipSecurityService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.ownershipSecurityService = ownershipSecurityService;
    }

    /**
     * Tool 1: getCustomerOrders(authenticatedUserId)
     */
    @Transactional(readOnly = true)
    public List<OrderService.OrderSummaryDto> getCustomerOrders(Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Quý khách cần đăng nhập để tra cứu đơn hàng");
        }
        return orderService.getCustomerOrders(authenticatedUserId);
    }

    /**
     * Tool 2: getOrderDetail(authenticatedUserId, orderCode)
     * Kiểm tra chặt quyền sở hữu: nếu đơn thuộc về userId khác, ném ngay 403 UNAUTHORIZED_ACCESS (AT-12).
     */
    @Transactional(readOnly = true)
    public OrderService.OrderDetailDto getOrderDetailByCode(Long authenticatedUserId, String orderCode) {
        if (authenticatedUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Quý khách cần đăng nhập để tra cứu đơn hàng");
        }
        OrderEntity order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mã đơn hàng: " + orderCode));

        ownershipSecurityService.assertCustomerOwnsOrder(authenticatedUserId, order);

        return orderService.getOrderDetailForCustomer(authenticatedUserId, order.getId());
    }
}
