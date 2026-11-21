package vn.haui.o2ofood.modules.ordering;

import jakarta.persistence.*;
import vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus;
import vn.haui.o2ofood.modules.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "commission_policy_id", nullable = false)
    private Long commissionPolicyId;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 30)
    private FulfillmentStatus fulfillmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "cancel_reason", length = 60)
    private String cancelReason;

    @Column(name = "cutoff_at", nullable = false)
    private OffsetDateTime cutoffAt;

    @Column(name = "pickup_start_at", nullable = false)
    private OffsetDateTime pickupStartAt;

    @Column(name = "auto_complete_at", nullable = false)
    private OffsetDateTime autoCompleteAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_type", length = 30)
    private CompletionType completionType;

    @Column(name = "customer_confirmed_at")
    private OffsetDateTime customerConfirmedAt;

    @Column(name = "physical_handed_over_at")
    private OffsetDateTime physicalHandedOverAt;

    @Column(name = "pickup_qr_token_hash", length = 128)
    private String pickupQrTokenHash;

    @Column(name = "pickup_qr_used_at")
    private OffsetDateTime pickupQrUsedAt;

    @Column(name = "has_open_issue", nullable = false)
    private Boolean hasOpenIssue = false;

    @Column(name = "is_unclaimed_at_agent", nullable = false)
    private Boolean isUnclaimedAtAgent = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public OrderEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getPickupPointId() {
        return pickupPointId;
    }

    public void setPickupPointId(Long pickupPointId) {
        this.pickupPointId = pickupPointId;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getCommissionPolicyId() {
        return commissionPolicyId;
    }

    public void setCommissionPolicyId(Long commissionPolicyId) {
        this.commissionPolicyId = commissionPolicyId;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public FulfillmentStatus getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public void setFulfillmentStatus(FulfillmentStatus fulfillmentStatus) {
        this.fulfillmentStatus = fulfillmentStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public OffsetDateTime getCutoffAt() {
        return cutoffAt;
    }

    public void setCutoffAt(OffsetDateTime cutoffAt) {
        this.cutoffAt = cutoffAt;
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

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public CompletionType getCompletionType() {
        return completionType;
    }

    public void setCompletionType(CompletionType completionType) {
        this.completionType = completionType;
    }

    public OffsetDateTime getCustomerConfirmedAt() {
        return customerConfirmedAt;
    }

    public void setCustomerConfirmedAt(OffsetDateTime customerConfirmedAt) {
        this.customerConfirmedAt = customerConfirmedAt;
    }

    public OffsetDateTime getPhysicalHandedOverAt() {
        return physicalHandedOverAt;
    }

    public void setPhysicalHandedOverAt(OffsetDateTime physicalHandedOverAt) {
        this.physicalHandedOverAt = physicalHandedOverAt;
    }

    public String getPickupQrTokenHash() {
        return pickupQrTokenHash;
    }

    public void setPickupQrTokenHash(String pickupQrTokenHash) {
        this.pickupQrTokenHash = pickupQrTokenHash;
    }

    public OffsetDateTime getPickupQrUsedAt() {
        return pickupQrUsedAt;
    }

    public void setPickupQrUsedAt(OffsetDateTime pickupQrUsedAt) {
        this.pickupQrUsedAt = pickupQrUsedAt;
    }

    public Boolean getHasOpenIssue() {
        return hasOpenIssue;
    }

    public void setHasOpenIssue(Boolean hasOpenIssue) {
        this.hasOpenIssue = hasOpenIssue;
    }

    public Boolean getIsUnclaimedAtAgent() {
        return isUnclaimedAtAgent;
    }

    public void setIsUnclaimedAtAgent(Boolean unclaimedAtAgent) {
        isUnclaimedAtAgent = unclaimedAtAgent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
