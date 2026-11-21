package vn.haui.o2ofood.modules.payment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_attempt_id", nullable = false)
    private Long paymentAttemptId;

    @Column(name = "event_source", nullable = false, length = 30)
    private String eventSource;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String rawPayload;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    public PaymentEventEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public void setPaymentAttemptId(Long paymentAttemptId) {
        this.paymentAttemptId = paymentAttemptId;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
