package vn.haui.o2ofood.modules.exception;

/**
 * Máy trạng thái 4 (Thu hồi hàng lỗi về kho cách ly).
 */
public enum ReturnStatus {
    REQUESTED,
    APPROVED_FOR_PICKUP,
    DRIVER_PICKED_UP,
    IN_RETURN_TRANSIT,
    WAREHOUSE_RECEIVED,
    UNRECOVERABLE_WAIVED,
    CLOSED
}
