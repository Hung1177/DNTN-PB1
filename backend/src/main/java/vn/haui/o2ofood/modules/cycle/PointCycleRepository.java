package vn.haui.o2ofood.modules.cycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointCycleRepository extends JpaRepository<PointCycleEntity, Long> {
    Optional<PointCycleEntity> findByCycleIdAndPickupPointId(Long cycleId, Long pickupPointId);
    List<PointCycleEntity> findByPickupPointIdOrderByCycleIdAsc(Long pickupPointId);
}
