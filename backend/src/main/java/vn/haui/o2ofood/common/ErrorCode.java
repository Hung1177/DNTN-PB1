package vn.haui.o2ofood.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Sản phẩm trong đợt giao hàng đã hết hoặc không đủ số lượng khả dụng"),
    CUTOFF_PASSED(HttpStatus.BAD_REQUEST, "CUTOFF_PASSED", "Đơn hàng đã qua giờ chốt 00:00 nên không thể thực hiện thao tác này"),
    CUTOFF_TOO_CLOSE(HttpStatus.BAD_REQUEST, "CUTOFF_TOO_CLOSE", "Thời gian còn lại đến giờ chốt dưới 2 phút, vui lòng chọn đợt giao hàng kế tiếp"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Dữ liệu đơn hàng vừa được cập nhật bởi tiến trình khác, vui lòng tải lại"),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký bảo mật không hợp lệ"),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", "Bạn không có quyền truy cập tài nguyên này"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy dữ liệu yêu cầu"),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION", "Chuyển đổi trạng thái không hợp lệ"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
