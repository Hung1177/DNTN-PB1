package vn.haui.o2ofood.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.identity.PointAssignmentEntity;
import vn.haui.o2ofood.modules.identity.PointAssignmentRepository;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kiểm soát quyền sở hữu ở tầng dữ liệu (Row-level Ownership Filter — Zero-Trust UI):
 * - ROLE_CUSTOMER: Chỉ truy xuất đơn hàng của chính userId đang đăng nhập.
 * - ROLE_AGENT: Chỉ truy xuất đơn hàng/hoa hồng thuộc pickupPointId được phân công trong PointAssignments.
 * - ROLE_DRIVER: Chỉ truy xuất chuyến xe có driverId là chính mình.
 */
@Component("ownershipSecurity")
public class OwnershipSecurityService {

    private final OrderRepository orderRepository;
    private final PointAssignmentRepository pointAssignmentRepository;
    private final TimeProvider timeProvider;

    public OwnershipSecurityService(OrderRepository orderRepository,
                                    PointAssignmentRepository pointAssignmentRepository,
                                    TimeProvider timeProvider) {
        this.orderRepository = orderRepository;
        this.pointAssignmentRepository = pointAssignmentRepository;
        this.timeProvider = timeProvider;
    }

    public UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
    }

    public boolean isCurrentUser(Long targetUserId) {
        UserPrincipal principal = currentPrincipal();
        return principal.hasRole(SecurityConstants.ROLE_ADMIN) || principal.getId().equals(targetUserId);
    }

    public boolean isAssignedToPickupPoint(Long pickupPointId) {
        UserPrincipal principal = currentPrincipal();
        if (principal.hasRole(SecurityConstants.ROLE_ADMIN)
                || principal.hasRole(SecurityConstants.ROLE_DISPATCHER)
                || principal.hasRole(SecurityConstants.ROLE_WAREHOUSE)) {
            return true;
        }
        if (principal.hasRole(SecurityConstants.ROLE_AGENT)) {
            return pointAssignmentRepository.isAgentAssignedToPoint(
                    principal.getId(), pickupPointId, timeProvider.now()
            );
        }
        return false;
    }

    public Set<Long> getAssignedPickupPointIds(Long agentUserId) {
        List<PointAssignmentEntity> assignments =
                pointAssignmentRepository.findActiveAssignmentsByAgent(agentUserId, timeProvider.now());
        return assignments.stream()
                .map(PointAssignmentEntity::getPickupPointId)
                .collect(Collectors.toSet());
    }

    public boolean canAccessOrder(Long orderId) {
        UserPrincipal principal = currentPrincipal();
        if (principal.hasRole(SecurityConstants.ROLE_ADMIN)
                || principal.hasRole(SecurityConstants.ROLE_DISPATCHER)
                || principal.hasRole(SecurityConstants.ROLE_WAREHOUSE)) {
            return true;
        }
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (principal.hasRole(SecurityConstants.ROLE_CUSTOMER) && order.getCustomerId().equals(principal.getId())) {
            return true;
        }
        if (principal.hasRole(SecurityConstants.ROLE_AGENT)) {
            return pointAssignmentRepository.isAgentAssignedToPoint(
                    principal.getId(), order.getPickupPointId(), timeProvider.now()
            );
        }
        return false;
    }

    public void assertCustomerOwnsOrder(Long authenticatedCustomerId, OrderEntity order) {
        if (!order.getCustomerId().equals(authenticatedCustomerId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Bạn không có quyền truy cập đơn hàng của người khác");
        }
    }
}
