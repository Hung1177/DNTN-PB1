package vn.haui.o2ofood.modules.payment;

/**
 * Máy trạng thái 3 (Thanh toán & Hoàn tiền).
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    LATE_RECONCILIATION,
    REFUND_REQUESTED,
    REFUND_APPROVED,
    REFUNDING,
    REFUNDED,
    REFUND_FAILED
}
