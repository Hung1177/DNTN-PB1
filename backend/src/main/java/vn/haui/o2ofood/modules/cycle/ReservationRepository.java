package vn.haui.o2ofood.modules.cycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    List<ReservationEntity> findByOrderIdOrderByVariantIdAsc(Long orderId);

    @Query("""
            SELECT DISTINCT r.orderId FROM ReservationEntity r
            WHERE r.status = vn.haui.o2ofood.modules.cycle.ReservationStatus.ACTIVE
              AND r.expiresAt < :now
            """)
    List<Long> findDistinctOrderIdsWithExpiredActiveReservations(@Param("now") OffsetDateTime now);
}
