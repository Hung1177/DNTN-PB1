package vn.haui.o2ofood.modules.ordering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLotAllocationRepository extends JpaRepository<OrderLotAllocationEntity, Long> {
    List<OrderLotAllocationEntity> findByOrderItemIdOrderByIdAsc(Long orderItemId);
    List<OrderLotAllocationEntity> findByLotIdOrderByIdAsc(Long lotId);
}
