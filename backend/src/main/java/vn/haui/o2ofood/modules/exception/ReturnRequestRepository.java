package vn.haui.o2ofood.modules.exception;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, Long> {
    List<ReturnRequestEntity> findByOrderIdOrderByIdAsc(Long orderId);
    List<ReturnRequestEntity> findByPickupPointIdOrderByIdDesc(Long pickupPointId);
}
