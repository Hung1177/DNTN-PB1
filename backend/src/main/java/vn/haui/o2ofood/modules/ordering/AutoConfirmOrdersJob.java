package vn.haui.o2ofood.modules.ordering;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.cycle.OrderCycleEntity;
import vn.haui.o2ofood.modules.cycle.OrderCycleRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tác vụ tự động chốt và xác nhận đơn lúc 00:00:00 Ngày D+1 (Mục 2, Mục 4.4, AT-04):
 * - Chạy theo cron đúng 00:00:00 mỗi ngày theo múi giờ Asia/Ho_Chi_Minh.
 * - Có cơ chế chạy bù (Catch-up Recovery) mỗi 5 phút và ngay khi ứng dụng khởi động lại (ApplicationReadyEvent).
 * - Sử dụng @Version trên OrderEntity để chống đua dữ liệu với khách bấm Hủy đơn lúc 23:59:59.
 */
@Component
public class AutoConfirmOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmOrdersJob.class);

    private final OrderRepository orderRepository;
    private final OrderCycleRepository orderCycleRepository;
    private final JobRunRepository jobRunRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public AutoConfirmOrdersJob(OrderRepository orderRepository,
                                OrderCycleRepository orderCycleRepository,
                                JobRunRepository jobRunRepository,
                                OutboxEventRepository outboxEventRepository,
                                TimeProvider timeProvider,
                                TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.orderCycleRepository = orderCycleRepository;
        this.jobRunRepository = jobRunRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCatchUp() {
        try {
            confirmEligibleOrders();
        } catch (Exception ex) {
            log.debug("Bỏ qua quét bù AutoConfirmOrdersJob lúc khởi động: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = "AutoConfirmOrdersJob", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10M")
    public int runMidnightAutoConfirm() {
        return confirmEligibleOrders();
    }

    @Scheduled(fixedDelayString = "${app.jobs.auto-confirm-catchup-ms:300000}", initialDelayString = "60000")
    @SchedulerLock(name = "AutoConfirmOrdersJob", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10M")
    public int runPeriodicCatchUp() {
        return confirmEligibleOrders();
    }

    public int confirmEligibleOrders() {
        OffsetDateTime startedAt = timeProvider.now();
        List<OrderEntity> eligibleOrders = orderRepository.findPaidOrdersReadyForAutoConfirm(startedAt);

        int confirmedCount = 0;
        for (OrderEntity candidate : eligibleOrders) {
            try {
                Boolean success = transactionTemplate.execute(status -> confirmSingleOrder(candidate.getId(), timeProvider.now()));
                if (Boolean.TRUE.equals(success)) {
                    confirmedCount++;
                }
            } catch (Exception ex) {
                log.warn("Không thể tự xác nhận orderId={} do xung đột đồng thời (@Version): {}", candidate.getId(), ex.getMessage());
            }
        }

        // Khóa các chu kỳ đặt hàng đã qua mốc auto_confirm_at
        List<OrderCycleEntity> pastCycles = orderCycleRepository.findOpenCyclesPastAutoConfirm(startedAt);
        for (OrderCycleEntity cycle : pastCycles) {
            transactionTemplate.executeWithoutResult(status -> {
                OrderCycleEntity managedCycle = orderCycleRepository.findById(cycle.getId()).orElse(null);
                if (managedCycle != null && "OPEN".equalsIgnoreCase(managedCycle.getStatus())) {
                    managedCycle.setStatus("LOCKED");
                    orderCycleRepository.save(managedCycle);
                }
            });
        }

        if (confirmedCount > 0 || !pastCycles.isEmpty()) {
            JobRunEntity jobRun = new JobRunEntity();
            jobRun.setJobName("AutoConfirmOrdersJob");
            jobRun.setStartedAt(startedAt);
            jobRun.setFinishedAt(timeProvider.now());
            jobRun.setProcessedCount(confirmedCount);
            jobRun.setStatus("SUCCESS");
            jobRunRepository.save(jobRun);
        }

        return confirmedCount;
    }

    public boolean confirmSingleOrder(Long orderId, OffsetDateTime now) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getOrderStatus() != OrderStatus.PAID_WAITING_CUTOFF) {
            return false;
        }
        if (now.isBefore(order.getCutoffAt())) {
            return false;
        }

        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(now);
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(String.valueOf(order.getId()));
        outbox.setEventType("ORDER_AUTO_CONFIRMED_AT_CUTOFF");
        outbox.setPayload("{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\"}");
        outbox.setStatus(OutboxEventStatus.PENDING);
        outbox.setCreatedAt(now);
        outboxEventRepository.save(outbox);

        return true;
    }
}
