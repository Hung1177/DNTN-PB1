package vn.haui.o2ofood.modules.fulfillment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "trips")
public class TripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_code", nullable = false, unique = true, length = 40)
    private String tripCode;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "trip_type", nullable = false, length = 30)
    private String tripType = "DELIVERY";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PLANNED";

    @Column(name = "departed_at")
    private OffsetDateTime departedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public TripEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTripCode() {
        return tripCode;
    }

    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getTripType() {
        return tripType;
    }

    public void setTripType(String tripType) {
        this.tripType = tripType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getDepartedAt() {
        return departedAt;
    }

    public void setDepartedAt(OffsetDateTime departedAt) {
        this.departedAt = departedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
