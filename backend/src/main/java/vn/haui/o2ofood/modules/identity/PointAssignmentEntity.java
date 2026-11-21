package vn.haui.o2ofood.modules.identity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "point_assignments")
public class PointAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    @Column(name = "agent_user_id", nullable = false)
    private Long agentUserId;

    @Column(name = "active_from", nullable = false)
    private OffsetDateTime activeFrom;

    @Column(name = "active_to")
    private OffsetDateTime activeTo;

    public PointAssignmentEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPickupPointId() {
        return pickupPointId;
    }

    public void setPickupPointId(Long pickupPointId) {
        this.pickupPointId = pickupPointId;
    }

    public Long getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(Long agentUserId) {
        this.agentUserId = agentUserId;
    }

    public OffsetDateTime getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(OffsetDateTime activeFrom) {
        this.activeFrom = activeFrom;
    }

    public OffsetDateTime getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(OffsetDateTime activeTo) {
        this.activeTo = activeTo;
    }
}
