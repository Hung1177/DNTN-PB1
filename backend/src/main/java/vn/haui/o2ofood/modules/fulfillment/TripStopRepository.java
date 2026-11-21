package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripStopRepository extends JpaRepository<TripStopEntity, Long> {
    List<TripStopEntity> findByTripIdOrderByStopSequenceAsc(Long tripId);
    List<TripStopEntity> findByPickupPointIdOrderByIdDesc(Long pickupPointId);
}
