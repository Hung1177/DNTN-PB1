package vn.haui.o2ofood.modules.cycle;

import jakarta.persistence.*;

@Entity
@Table(name = "point_cycles")
public class PointCycleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    @Column(name = "max_orders", nullable = false)
    private Integer maxOrders;

    @Column(name = "current_orders", nullable = false)
    private Integer currentOrders = 0;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "OPEN";

    public PointCycleEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getPickupPointId() {
        return pickupPointId;
    }

    public void setPickupPointId(Long pickupPointId) {
        this.pickupPointId = pickupPointId;
    }

    public Integer getMaxOrders() {
        return maxOrders;
    }

    public void setMaxOrders(Integer maxOrders) {
        this.maxOrders = maxOrders;
    }

    public Integer getCurrentOrders() {
        return currentOrders;
    }

    public void setCurrentOrders(Integer currentOrders) {
        this.currentOrders = currentOrders;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
