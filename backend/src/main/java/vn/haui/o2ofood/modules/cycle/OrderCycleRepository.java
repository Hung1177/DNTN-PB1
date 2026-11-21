package vn.haui.o2ofood.modules.cycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderCycleRepository extends JpaRepository<OrderCycleEntity, Long> {

    Optional<OrderCycleEntity> findByCycleCode(String cycleCode);

    @Query("""
            SELECT oc FROM OrderCycleEntity oc
            WHERE oc.status = 'OPEN'
              AND oc.cutoffAt > :now
            ORDER BY oc.deliveryDate ASC, oc.cutoffAt ASC
            """)
    List<OrderCycleEntity> findOpenCyclesAfter(@Param("now") OffsetDateTime now);

    @Query("""
            SELECT oc FROM OrderCycleEntity oc
            WHERE oc.status = 'OPEN'
              AND oc.autoConfirmAt <= :now
            """)
    List<OrderCycleEntity> findOpenCyclesPastAutoConfirm(@Param("now") OffsetDateTime now);
}
