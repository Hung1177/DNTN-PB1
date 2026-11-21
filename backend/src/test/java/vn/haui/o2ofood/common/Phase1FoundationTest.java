package vn.haui.o2ofood.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.haui.o2ofood.modules.commission.CommissionEntryType;
import vn.haui.o2ofood.modules.exception.ReturnStatus;
import vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus;
import vn.haui.o2ofood.modules.identity.RoleCode;
import vn.haui.o2ofood.modules.ordering.CompletionType;
import vn.haui.o2ofood.modules.ordering.OrderStatus;
import vn.haui.o2ofood.modules.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Phase1FoundationTest {

    private final TimeProvider timeProvider = new TimeProvider();

    @AfterEach
    void tearDown() {
        timeProvider.resetToSystemClock();
    }

    @Test
    void timeProviderAlwaysUsesAsiaHoChiMinhOffset() {
        OffsetDateTime now = timeProvider.now();
        assertEquals(TimeProvider.VN_OFFSET, now.getOffset());

        OffsetDateTime cutoffMoment = OffsetDateTime.parse("2026-11-17T23:59:59+07:00");
        timeProvider.setFixedTime(cutoffMoment);
        assertEquals(cutoffMoment, timeProvider.now());
        assertEquals(2026, timeProvider.today().getYear());
        assertEquals(11, timeProvider.today().getMonthValue());
        assertEquals(17, timeProvider.today().getDayOfMonth());
    }

    @Test
    void moneyUtilsNormalizesToScale2WithHalfUp() {
        BigDecimal price = MoneyUtils.of(85000);
        assertEquals(new BigDecimal("85000.00"), price);
        assertEquals(new BigDecimal("255000.00"), MoneyUtils.multiply(price, 3));
    }

    @Test
    void fiveCoupledStateMachinesAndSixRolesAreDefined() {
        assertEquals(6, RoleCode.values().length);
        assertEquals(6, OrderStatus.values().length);
        assertEquals(2, CompletionType.values().length);
        assertEquals(7, FulfillmentStatus.values().length);
        assertEquals(8, PaymentStatus.values().length);
        assertEquals(7, ReturnStatus.values().length);
        assertEquals(3, CommissionEntryType.values().length);
        assertNotNull(OrderStatus.valueOf("PAID_WAITING_CUTOFF"));
        assertNotNull(FulfillmentStatus.valueOf("READY_FOR_PICKUP"));
        assertNotNull(CompletionType.valueOf("SYSTEM_TIMEOUT"));
    }
}
