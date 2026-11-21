package vn.haui.o2ofood.modules.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PointAssignmentRepository extends JpaRepository<PointAssignmentEntity, Long> {

    @Query("""
            SELECT pa FROM PointAssignmentEntity pa
            WHERE pa.agentUserId = :agentUserId
              AND pa.activeFrom <= :now
              AND (pa.activeTo IS NULL OR pa.activeTo >= :now)
            """)
    List<PointAssignmentEntity> findActiveAssignmentsByAgent(@Param("agentUserId") Long agentUserId,
                                                             @Param("now") OffsetDateTime now);

    @Query("""
            SELECT COUNT(pa) > 0 FROM PointAssignmentEntity pa
            WHERE pa.agentUserId = :agentUserId
              AND pa.pickupPointId = :pickupPointId
              AND pa.activeFrom <= :now
              AND (pa.activeTo IS NULL OR pa.activeTo >= :now)
            """)
    boolean isAgentAssignedToPoint(@Param("agentUserId") Long agentUserId,
                                   @Param("pickupPointId") Long pickupPointId,
                                   @Param("now") OffsetDateTime now);
}
