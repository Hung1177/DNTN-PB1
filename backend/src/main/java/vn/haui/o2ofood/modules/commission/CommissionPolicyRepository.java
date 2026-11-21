package vn.haui.o2ofood.modules.commission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicyEntity, Long> {

    Optional<CommissionPolicyEntity> findByPolicyCode(String policyCode);

    @Query("""
            SELECT cp FROM CommissionPolicyEntity cp
            WHERE cp.effectiveFrom <= :now
              AND (cp.effectiveTo IS NULL OR cp.effectiveTo >= :now)
            ORDER BY cp.id ASC
            """)
    List<CommissionPolicyEntity> findActivePolicies(@Param("now") OffsetDateTime now);
}
