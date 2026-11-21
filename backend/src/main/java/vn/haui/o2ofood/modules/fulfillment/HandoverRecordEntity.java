package vn.haui.o2ofood.modules.fulfillment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "handover_records")
public class HandoverRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_type", nullable = false, length = 40)
    private String handoverType;

    @Column(name = "trip_stop_id")
    private Long tripStopId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "total_expected_packages", nullable = false)
    private Integer totalExpectedPackages;

    @Column(name = "accepted_packages", nullable = false)
    private Integer acceptedPackages;

    @Column(name = "missing_packages", nullable = false)
    private Integer missingPackages;

    @Column(name = "damaged_packages", nullable = false)
    private Integer damagedPackages;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "signed_at", nullable = false)
    private OffsetDateTime signedAt;

    public HandoverRecordEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHandoverType() {
        return handoverType;
    }

    public void setHandoverType(String handoverType) {
        this.handoverType = handoverType;
    }

    public Long getTripStopId() {
        return tripStopId;
    }

    public void setTripStopId(Long tripStopId) {
        this.tripStopId = tripStopId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public Integer getTotalExpectedPackages() {
        return totalExpectedPackages;
    }

    public void setTotalExpectedPackages(Integer totalExpectedPackages) {
        this.totalExpectedPackages = totalExpectedPackages;
    }

    public Integer getAcceptedPackages() {
        return acceptedPackages;
    }

    public void setAcceptedPackages(Integer acceptedPackages) {
        this.acceptedPackages = acceptedPackages;
    }

    public Integer getMissingPackages() {
        return missingPackages;
    }

    public void setMissingPackages(Integer missingPackages) {
        this.missingPackages = missingPackages;
    }

    public Integer getDamagedPackages() {
        return damagedPackages;
    }

    public void setDamagedPackages(Integer damagedPackages) {
        this.damagedPackages = damagedPackages;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(OffsetDateTime signedAt) {
        this.signedAt = signedAt;
    }
}
