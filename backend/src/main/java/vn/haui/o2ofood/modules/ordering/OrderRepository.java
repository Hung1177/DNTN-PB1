package vn.haui.o2ofood.modules.ordering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderCode(String orderCode);

    Optional<OrderEntity> findByIdAndCustomerId(Long id, Long customerId);

    Optional<OrderEntity> findByOrderCodeAndCustomerId(String orderCode, Long customerId);

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<OrderEntity> findByPickupPointIdOrderByCreatedAtDesc(Long pickupPointId);

    List<OrderEntity> findByPickupPointIdAndIsUnclaimedAtAgentTrueOrderByIdAsc(Long pickupPointId);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.orderStatus = vn.haui.o2ofood.modules.ordering.OrderStatus.PAID_WAITING_CUTOFF
              AND o.cutoffAt <= :now
            ORDER BY o.id ASC
            """)
    List<OrderEntity> findPaidOrdersReadyForAutoConfirm(@Param("now") OffsetDateTime now);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.cycleId = :cycleId
              AND o.orderStatus IN (
                  vn.haui.o2ofood.modules.ordering.OrderStatus.CONFIRMED,
                  vn.haui.o2ofood.modules.ordering.OrderStatus.PROCESSING
              )
            ORDER BY o.pickupPointId ASC, o.id ASC
            """)
    List<OrderEntity> findConfirmedOrdersForWarehousePacking(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.orderStatus IN (
                vn.haui.o2ofood.modules.ordering.OrderStatus.CONFIRMED,
                vn.haui.o2ofood.modules.ordering.OrderStatus.PROCESSING
            )
              AND o.fulfillmentStatus IN (
                vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus.READY_FOR_PICKUP,
                vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus.HANDED_OVER
              )
              AND o.hasOpenIssue = false
              AND o.autoCompleteAt <= :now
            ORDER BY o.id ASC
            """)
    List<OrderEntity> findEligibleOrdersForAutoComplete(@Param("now") OffsetDateTime now);
}
