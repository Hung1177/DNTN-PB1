package vn.haui.o2ofood.modules.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
    List<RefundEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
