package vn.haui.o2ofood.modules.ordering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
