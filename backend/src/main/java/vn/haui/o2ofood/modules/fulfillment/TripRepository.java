package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
    Optional<TripEntity> findByTripCode(String tripCode);
    List<TripEntity> findByDriverIdOrderByIdDesc(Long driverId);
    List<TripEntity> findByCycleIdOrderByIdAsc(Long cycleId);
}
