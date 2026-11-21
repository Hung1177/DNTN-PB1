package vn.haui.o2ofood.modules.fulfillment;

import jakarta.persistence.*;

@Entity
@Table(name = "packages")
public class PackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "trip_stop_id")
    private Long tripStopId;

    @Column(name = "package_code", nullable = false, unique = true, length = 50)
    private String packageCode;

    @Column(name = "qr_payload", nullable = false, unique = true, length = 200)
    private String qrPayload;

    @Column(name = "package_seq", nullable = false)
    private Integer packageSeq;

    @Column(name = "total_packages_in_order", nullable = false)
    private Integer totalPackagesInOrder;

    @Column(name = "storage_type", nullable = false, length = 30)
    private String storageType;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PACKED";

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public PackageEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getTripStopId() {
        return tripStopId;
    }

    public void setTripStopId(Long tripStopId) {
        this.tripStopId = tripStopId;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public Integer getPackageSeq() {
        return packageSeq;
    }

    public void setPackageSeq(Integer packageSeq) {
        this.packageSeq = packageSeq;
    }

    public Integer getTotalPackagesInOrder() {
        return totalPackagesInOrder;
    }

    public void setTotalPackagesInOrder(Integer totalPackagesInOrder) {
        this.totalPackagesInOrder = totalPackagesInOrder;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
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
