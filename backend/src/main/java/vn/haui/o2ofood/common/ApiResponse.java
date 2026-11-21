package vn.haui.o2ofood.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        OffsetDateTime serverTime
) {
    public static <T> ApiResponse<T> ok(T data, OffsetDateTime serverTime) {
        return new ApiResponse<>(true, "SUCCESS", "Thành công", data, serverTime);
    }

    public static <T> ApiResponse<T> ok(String message, T data, OffsetDateTime serverTime) {
        return new ApiResponse<>(true, "SUCCESS", message, data, serverTime);
    }

    public static <T> ApiResponse<T> error(String code, String message, OffsetDateTime serverTime) {
        return new ApiResponse<>(false, code, message, null, serverTime);
    }
}
