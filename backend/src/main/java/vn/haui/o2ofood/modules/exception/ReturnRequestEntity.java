package vn.haui.o2ofood.modules.exception;

import jakarta.persistence.*;

@Entity
@Table(name = "return_requests")
public class ReturnRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    @Column(name = "return_trip_id")
    private Long returnTripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(name = "warehouse_inspection_note", length = 500)
    private String warehouseInspectionNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ReturnRequestEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPickupPointId() {
        return pickupPointId;
    }

    public void setPickupPointId(Long pickupPointId) {
        this.pickupPointId = pickupPointId;
    }

    public Long getReturnTripId() {
        return returnTripId;
    }

    public void setReturnTripId(Long returnTripId) {
        this.returnTripId = returnTripId;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public void setStatus(ReturnStatus status) {
        this.status = status;
    }

    public String getWarehouseInspectionNote() {
        return warehouseInspectionNote;
    }

    public void setWarehouseInspectionNote(String warehouseInspectionNote) {
        this.warehouseInspectionNote = warehouseInspectionNote;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
