package vn.haui.o2ofood.modules.commission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CommissionLedgerRepository extends JpaRepository<CommissionLedgerEntity, Long> {

    List<CommissionLedgerEntity> findByOrderIdOrderByIdAsc(Long orderId);

    List<CommissionLedgerEntity> findByPickupPointIdOrderByCreatedAtDescIdDesc(Long pickupPointId);

    Optional<CommissionLedgerEntity> findByOrderIdAndEntryType(Long orderId, CommissionEntryType entryType);

    boolean existsByOrderIdAndEntryType(Long orderId, CommissionEntryType entryType);

    @Query("""
            SELECT COALESCE(SUM(cl.amount), 0)
            FROM CommissionLedgerEntity cl
            WHERE cl.pickupPointId = :pickupPointId
            """)
    BigDecimal calculateNetCommissionBalance(@Param("pickupPointId") Long pickupPointId);
}
