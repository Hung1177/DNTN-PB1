package vn.haui.o2ofood.modules.ordering;

/**
 * Máy trạng thái 1 (Thương mại) — Tách biệt hoàn toàn khỏi trạng thái giao nhận vật lý (FulfillmentStatus).
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID_WAITING_CUTOFF,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    CANCELLED
}
