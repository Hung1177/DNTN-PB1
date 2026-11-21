package vn.haui.o2ofood.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Chuẩn hóa tiền tệ DECIMAL(18, 2) và làm tròn HALF_UP trên toàn hệ thống.
 */
public final class MoneyUtils {

    public static final int CURRENCY_SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CURRENCY_SCALE, ROUNDING_MODE);

    private MoneyUtils() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(CURRENCY_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal of(long vndAmount) {
        return BigDecimal.valueOf(vndAmount).setScale(CURRENCY_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        return normalize(unitPrice).multiply(BigDecimal.valueOf(quantity)).setScale(CURRENCY_SCALE, ROUNDING_MODE);
    }
}
