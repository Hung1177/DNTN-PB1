package vn.haui.o2ofood.modules.fulfillment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "trip_stops")
public class TripStopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "eta_at")
    private OffsetDateTime etaAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "handover_completed_at")
    private OffsetDateTime handoverCompletedAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    public TripStopEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getPickupPointId() {
        return pickupPointId;
    }

    public void setPickupPointId(Long pickupPointId) {
        this.pickupPointId = pickupPointId;
    }

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    public OffsetDateTime getEtaAt() {
        return etaAt;
    }

    public void setEtaAt(OffsetDateTime etaAt) {
        this.etaAt = etaAt;
    }

    public OffsetDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(OffsetDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public OffsetDateTime getHandoverCompletedAt() {
        return handoverCompletedAt;
    }

    public void setHandoverCompletedAt(OffsetDateTime handoverCompletedAt) {
        this.handoverCompletedAt = handoverCompletedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
