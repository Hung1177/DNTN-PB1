package vn.haui.o2ofood.modules.cycle;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_cycles")
public class OrderCycleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_code", nullable = false, unique = true, length = 40)
    private String cycleCode;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "open_at", nullable = false)
    private OffsetDateTime openAt;

    @Column(name = "cutoff_at", nullable = false)
    private OffsetDateTime cutoffAt;

    @Column(name = "auto_confirm_at", nullable = false)
    private OffsetDateTime autoConfirmAt;

    @Column(name = "warehouse_pack_target_at", nullable = false)
    private OffsetDateTime warehousePackTargetAt;

    @Column(name = "dispatch_start_at", nullable = false)
    private OffsetDateTime dispatchStartAt;

    @Column(name = "pickup_start_at", nullable = false)
    private OffsetDateTime pickupStartAt;

    @Column(name = "auto_complete_at", nullable = false)
    private OffsetDateTime autoCompleteAt;

    @Column(name = "payment_timeout_minutes", nullable = false)
    private Integer paymentTimeoutMinutes = 30;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "OPEN";

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public OrderCycleEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCycleCode() {
        return cycleCode;
    }

    public void setCycleCode(String cycleCode) {
        this.cycleCode = cycleCode;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public OffsetDateTime getOpenAt() {
        return openAt;
    }

    public void setOpenAt(OffsetDateTime openAt) {
        this.openAt = openAt;
    }

    public OffsetDateTime getCutoffAt() {
        return cutoffAt;
    }

    public void setCutoffAt(OffsetDateTime cutoffAt) {
        this.cutoffAt = cutoffAt;
    }

    public OffsetDateTime getAutoConfirmAt() {
        return autoConfirmAt;
    }

    public void setAutoConfirmAt(OffsetDateTime autoConfirmAt) {
        this.autoConfirmAt = autoConfirmAt;
    }

    public OffsetDateTime getWarehousePackTargetAt() {
        return warehousePackTargetAt;
    }

    public void setWarehousePackTargetAt(OffsetDateTime warehousePackTargetAt) {
        this.warehousePackTargetAt = warehousePackTargetAt;
    }

    public OffsetDateTime getDispatchStartAt() {
        return dispatchStartAt;
    }

    public void setDispatchStartAt(OffsetDateTime dispatchStartAt) {
        this.dispatchStartAt = dispatchStartAt;
    }

    public OffsetDateTime getPickupStartAt() {
        return pickupStartAt;
    }

    public void setPickupStartAt(OffsetDateTime pickupStartAt) {
        this.pickupStartAt = pickupStartAt;
    }

    public OffsetDateTime getAutoCompleteAt() {
        return autoCompleteAt;
    }

    public void setAutoCompleteAt(OffsetDateTime autoCompleteAt) {
        this.autoCompleteAt = autoCompleteAt;
    }

    public Integer getPaymentTimeoutMinutes() {
        return paymentTimeoutMinutes;
    }

    public void setPaymentTimeoutMinutes(Integer paymentTimeoutMinutes) {
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
