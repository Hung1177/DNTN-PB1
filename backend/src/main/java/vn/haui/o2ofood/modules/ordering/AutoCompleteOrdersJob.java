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
import vn.haui.o2ofood.modules.commission.CommissionService;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tác vụ tự động hoàn tất đơn hàng & tính hoa hồng lúc 00:00:00 Ngày D+2 (Mục 1.5, Mục 2, Mục 4.4, AT-09, AT-10):
 * - Các đơn ở trạng thái READY_FOR_PICKUP hoặc HANDED_OVER không có sự cố đang mở (has_open_issue = false)
 *   và đã đến mốc auto_complete_at sẽ tự chuyển sang COMPLETED với completion_type = SYSTEM_TIMEOUT.
 * - Tách bạch trạng thái thương mại và vật lý (AT-10): nếu khách chưa đến lấy hàng (physical_handed_over_at == NULL),
 *   giữ nguyên physical_handed_over_at = NULL và gắn cờ is_unclaimed_at_agent = true để đưa vào
 *   Danh sách hàng tồn khách chưa lấy tại đại lý (Unclaimed Inventory).
 * - Ghi nhận hoa hồng đúng 1 lần vào CommissionLedger.
 */
@Component
public class AutoCompleteOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(AutoCompleteOrdersJob.class);

    private final OrderRepository orderRepository;
    private final CommissionService commissionService;
    private final JobRunRepository jobRunRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public AutoCompleteOrdersJob(OrderRepository orderRepository,
                                 CommissionService commissionService,
                                 JobRunRepository jobRunRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 TimeProvider timeProvider,
                                 TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.commissionService = commissionService;
        this.jobRunRepository = jobRunRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCatchUp() {
        try {
            completeEligibleOrders();
        } catch (Exception ex) {
            log.debug("Bỏ qua quét bù AutoCompleteOrdersJob lúc khởi động: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = "AutoCompleteOrdersJob", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10M")
    public int runMidnightAutoComplete() {
        return completeEligibleOrders();
    }

    @Scheduled(fixedDelayString = "${app.jobs.auto-complete-catchup-ms:300000}", initialDelayString = "90000")
    @SchedulerLock(name = "AutoCompleteOrdersJob", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10M")
    public int runPeriodicCatchUp() {
        return completeEligibleOrders();
    }

    public int completeEligibleOrders() {
        OffsetDateTime startedAt = timeProvider.now();
        List<OrderEntity> candidates = orderRepository.findEligibleOrdersForAutoComplete(startedAt);
        if (candidates.isEmpty()) {
            return 0;
        }

        int completedCount = 0;
        for (OrderEntity candidate : candidates) {
            try {
                Boolean ok = transactionTemplate.execute(
                        status -> completeSingleOrderByTimeout(candidate.getId(), timeProvider.now())
                );
                if (Boolean.TRUE.equals(ok)) {
                    completedCount++;
                }
            } catch (Exception ex) {
                log.warn("Không thể tự hoàn tất orderId={} do xung đột đồng thời (@Version): {}", candidate.getId(), ex.getMessage());
            }
        }

        if (completedCount > 0) {
            JobRunEntity jobRun = new JobRunEntity();
            jobRun.setJobName("AutoCompleteOrdersJob");
            jobRun.setStartedAt(startedAt);
            jobRun.setFinishedAt(timeProvider.now());
            jobRun.setProcessedCount(completedCount);
            jobRun.setStatus("SUCCESS");
            jobRunRepository.save(jobRun);
        }

        return completedCount;
    }

    public boolean completeSingleOrderByTimeout(Long orderId, OffsetDateTime now) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getOrderStatus() == OrderStatus.COMPLETED) {
            return false;
        }
        if (Boolean.TRUE.equals(order.getHasOpenIssue()) || now.isBefore(order.getAutoCompleteAt())) {
            return false;
        }

        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        order.setCompletionType(CompletionType.SYSTEM_TIMEOUT);

        // AT-10: Nếu khách chưa đến lấy hàng vật lý (physical_handed_over_at == NULL),
        // tuyệt đối giữ nguyên physical_handed_over_at = NULL và đưa vào danh sách hàng tồn chưa lấy tại đại lý.
        if (order.getPhysicalHandedOverAt() == null) {
            order.setIsUnclaimedAtAgent(true);
        }

        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        commissionService.recordCreditCommissionOnce(order, now);

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(String.valueOf(order.getId()));
        outbox.setEventType("ORDER_AUTO_COMPLETED_TIMEOUT");
        outbox.setPayload("{\"orderId\":" + order.getId() + ",\"unclaimedAtAgent\":" + order.getIsUnclaimedAtAgent() + "}");
        outbox.setStatus(OutboxEventStatus.PENDING);
        outbox.setCreatedAt(now);
        outboxEventRepository.save(outbox);

        return true;
    }
}
