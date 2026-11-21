package vn.haui.o2ofood.modules.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PickupPointRepository extends JpaRepository<PickupPointEntity, Long> {
    List<PickupPointEntity> findByStatusOrderByIdAsc(String status);
    Optional<PickupPointEntity> findByCode(String code);
}
