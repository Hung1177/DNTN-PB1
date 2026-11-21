package vn.haui.o2ofood.modules.ordering;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceRepository;
import vn.haui.o2ofood.modules.cycle.ReservationEntity;
import vn.haui.o2ofood.modules.cycle.ReservationRepository;
import vn.haui.o2ofood.modules.cycle.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tác vụ nền thu hồi giữ tồn quá hạn (Mục 4.2, AT-02):
 * Chạy định kỳ mỗi 60 giây (có khóa ShedLock), tìm các bản ghi reservations có
 * status = 'ACTIVE' AND expires_at < now, nếu đơn vẫn đang PENDING_PAYMENT thì chuyển đơn sang
 * CANCELLED (lý do PAYMENT_TIMEOUT), chuyển reservation sang EXPIRED và hoàn trả available_qty đúng 1 lần.
 */
@Component
public class ReservationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupJob.class);

    private final ReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final JobRunRepository jobRunRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public ReservationCleanupJob(ReservationRepository reservationRepository,
                                 OrderRepository orderRepository,
                                 InventoryBalanceRepository inventoryBalanceRepository,
                                 JobRunRepository jobRunRepository,
                                 TimeProvider timeProvider,
                                 TransactionTemplate transactionTemplate) {
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.jobRunRepository = jobRunRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.jobs.reservation-cleanup-ms:60000}")
    @SchedulerLock(name = "ReservationCleanupJob", lockAtLeastFor = "PT2S", lockAtMostFor = "PT5M")
    public int cleanupExpiredReservations() {
        OffsetDateTime startedAt = timeProvider.now();
        List<Long> expiredOrderIds = reservationRepository.findDistinctOrderIdsWithExpiredActiveReservations(startedAt);
        if (expiredOrderIds.isEmpty()) {
            return 0;
        }

        int processedOrders = 0;
        for (Long orderId : expiredOrderIds) {
            try {
                Boolean expired = transactionTemplate.execute(status -> expireSingleOrderReservations(orderId, timeProvider.now()));
                if (Boolean.TRUE.equals(expired)) {
                    processedOrders++;
                }
            } catch (Exception ex) {
                log.warn("Không thể thu hồi giữ tồn cho orderId={} do xung đột đồng thời: {}", orderId, ex.getMessage());
            }
        }

        JobRunEntity jobRun = new JobRunEntity();
        jobRun.setJobName("ReservationCleanupJob");
        jobRun.setStartedAt(startedAt);
        jobRun.setFinishedAt(timeProvider.now());
        jobRun.setProcessedCount(processedOrders);
        jobRun.setStatus("SUCCESS");
        jobRunRepository.save(jobRun);

        return processedOrders;
    }

    protected boolean expireSingleOrderReservations(Long orderId, OffsetDateTime now) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }

        List<ReservationEntity> reservations = reservationRepository.findByOrderIdOrderByVariantIdAsc(orderId);
        boolean anyReleased = false;
        for (ReservationEntity reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE && reservation.getExpiresAt().isBefore(now)) {
                int released = inventoryBalanceRepository.releaseReservedStock(
                        reservation.getCycleId(),
                        reservation.getVariantId(),
                        reservation.getReservedQty(),
                        now
                );
                if (released > 0) {
                    anyReleased = true;
                }
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
            }
        }

        if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setCancelReason("PAYMENT_TIMEOUT");
            order.setUpdatedAt(now);
            orderRepository.saveAndFlush(order);
            return true;
        }
        return anyReleased;
    }
}
