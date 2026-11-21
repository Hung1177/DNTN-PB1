package vn.haui.o2ofood.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chuẩn hóa đồng hồ máy chủ theo múi giờ Asia/Ho_Chi_Minh (UTC+07:00).
 * Tuyệt đối không tin tưởng đồng hồ gửi lên từ trình duyệt hoặc thiết bị Android của khách.
 * Hỗ trợ thay thế Clock trong các kịch bản kiểm thử nghiệm thu (AT-01 .. AT-12).
 */
@Component
public class TimeProvider {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);

    private final AtomicReference<Clock> clockRef =
            new AtomicReference<>(Clock.system(VN_ZONE));

    public OffsetDateTime now() {
        return OffsetDateTime.now(clockRef.get()).withOffsetSameInstant(VN_OFFSET);
    }

    public LocalDate today() {
        return LocalDate.now(clockRef.get());
    }

    public LocalDateTime localDateTimeNow() {
        return LocalDateTime.now(clockRef.get());
    }

    public Instant instantNow() {
        return Instant.now(clockRef.get());
    }

    /**
     * Dùng trong Integration Tests để cố định thời điểm kiểm thử (ví dụ: 23:59:58 hoặc 00:00:00).
     */
    public void setFixedTime(OffsetDateTime fixedDateTime) {
        Instant instant = fixedDateTime.toInstant();
        this.clockRef.set(Clock.fixed(instant, VN_ZONE));
    }

    public void resetToSystemClock() {
        this.clockRef.set(Clock.system(VN_ZONE));
    }
}
