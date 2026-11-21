package vn.haui.o2ofood.modules.fulfillment;

import jakarta.persistence.*;

@Entity
@Table(name = "handover_items")
public class HandoverItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_record_id", nullable = false)
    private Long handoverRecordId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "condition_status", nullable = false, length = 30)
    private String conditionStatus;

    @Column(name = "evidence_image_url", length = 500)
    private String evidenceImageUrl;

    public HandoverItemEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHandoverRecordId() {
        return handoverRecordId;
    }

    public void setHandoverRecordId(Long handoverRecordId) {
        this.handoverRecordId = handoverRecordId;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public String getEvidenceImageUrl() {
        return evidenceImageUrl;
    }

    public void setEvidenceImageUrl(String evidenceImageUrl) {
        this.evidenceImageUrl = evidenceImageUrl;
    }
}
