package vn.haui.o2ofood.modules.fulfillment;

/**
 * Máy trạng thái 2 (Vật lý) — Tách biệt khỏi OrderStatus.
 */
public enum FulfillmentStatus {
    WAITING_PACKING,
    PACKED,
    IN_TRANSIT,
    AGENT_CHECKING,
    READY_FOR_PICKUP,
    HANDED_OVER,
    ISSUE_HOLD
}
