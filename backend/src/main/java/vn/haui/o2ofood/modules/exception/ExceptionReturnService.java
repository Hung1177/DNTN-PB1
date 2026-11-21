package vn.haui.o2ofood.modules.exception;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.catalog.StockMovementEntity;
import vn.haui.o2ofood.modules.catalog.StockMovementRepository;
import vn.haui.o2ofood.modules.commission.CommissionService;
import vn.haui.o2ofood.modules.fulfillment.HandoverRecordEntity;
import vn.haui.o2ofood.modules.fulfillment.HandoverRecordRepository;
import vn.haui.o2ofood.modules.fulfillment.PackageEntity;
import vn.haui.o2ofood.modules.fulfillment.PackageRepository;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.modules.ordering.OrderItemEntity;
import vn.haui.o2ofood.modules.ordering.OrderItemRepository;
import vn.haui.o2ofood.modules.ordering.OrderRepository;
import vn.haui.o2ofood.modules.payment.PaymentStatus;
import vn.haui.o2ofood.modules.payment.RefundEntity;
import vn.haui.o2ofood.modules.payment.RefundRepository;
import vn.haui.o2ofood.security.OwnershipSecurityService;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ExceptionReturnService {

    private final IssueRepository issueRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackageRepository packageRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CommissionService commissionService;
    private final OwnershipSecurityService ownershipSecurityService;
    private final TimeProvider timeProvider;

    public ExceptionReturnService(IssueRepository issueRepository,
                                  ReturnRequestRepository returnRequestRepository,
                                  RefundRepository refundRepository,
                                  OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  PackageRepository packageRepository,
                                  HandoverRecordRepository handoverRecordRepository,
                                  StockMovementRepository stockMovementRepository,
                                  CommissionService commissionService,
                                  OwnershipSecurityService ownershipSecurityService,
                                  TimeProvider timeProvider) {
        this.issueRepository = issueRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageRepository = packageRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.commissionService = commissionService;
        this.ownershipSecurityService = ownershipSecurityService;
        this.timeProvider = timeProvider;
    }

    /**
     * UC-C06: Khách hàng báo sự cố & yêu cầu hoàn tiền.
     * - Lập tức gắn cờ has_open_issue = true trên đơn hàng để chặn tác vụ tự hoàn tất lúc 00:00 D+2.
     * - Cho phép tạo khiếu nại cả khi đơn đã tự hoàn tất lúc 00:00 nhưng vẫn trong thời hạn khiếu nại.
     */
    @Transactional
    public ReportedIssueResult reportCustomerIssue(Long customerId, ReportIssueRequest request) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        ownershipSecurityService.assertCustomerOwnsOrder(customerId, order);

        order.setHasOpenIssue(true);
        order.setPaymentStatus(PaymentStatus.REFUND_REQUESTED);
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);

        IssueEntity issue = new IssueEntity();
        issue.setOrderId(order.getId());
        issue.setReportedByUserId(customerId);
        issue.setReporterRole("ROLE_CUSTOMER");
        issue.setIssueType(request.issueType());
        issue.setDescription(request.description());
        issue.setEvidenceUrls(request.evidenceUrls());
        issue.setStatus("OPEN");
        issue.setCreatedAt(now);
        issue = issueRepository.save(issue);

        ReturnRequestEntity returnReq = new ReturnRequestEntity();
        returnReq.setIssueId(issue.getId());
        returnReq.setOrderId(order.getId());
        returnReq.setPickupPointId(order.getPickupPointId());
        returnReq.setStatus(ReturnStatus.APPROVED_FOR_PICKUP);
        returnReq = returnRequestRepository.save(returnReq);

        return new ReportedIssueResult(issue.getId(), returnReq.getId(), order.getId(), order.getHasOpenIssue());
    }

    /**
     * UC-W04 & AT-11: Nhân viên kho kiểm nhận hàng hoàn về Kho cách ly hàng lỗi (QUARANTINE_DAMAGED_STOCK).
     * - Ghi biên bản DRIVER_TO_WAREHOUSE_RETURN.
     * - Ghi StockMovements (RETURN_TO_QUARANTINE) — Tuyệt đối KHÔNG cộng hàng hoàn về lại available_qty thương mại.
     * - Chuyển ReturnRequest sang WAREHOUSE_RECEIVED và tạo bản ghi Refund (PENDING_APPROVAL).
     */
    @Transactional
    public RefundEntity receiveReturnAtQuarantineWarehouse(Long warehouseUserId,
                                                           Long driverUserId,
                                                           Long returnRequestId,
                                                           String inspectionNote) {
        OffsetDateTime now = timeProvider.now();
        ReturnRequestEntity returnReq = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu thu hồi hàng hoàn"));
        OrderEntity order = orderRepository.findById(returnReq.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        List<PackageEntity> packages = packageRepository.findByOrderIdOrderByPackageSeqAsc(order.getId());
        int pkgCount = Math.max(packages.size(), 1);

        HandoverRecordEntity handover = new HandoverRecordEntity();
        handover.setHandoverType("DRIVER_TO_WAREHOUSE_RETURN");
        handover.setFromUserId(driverUserId);
        handover.setToUserId(warehouseUserId);
        handover.setTotalExpectedPackages(pkgCount);
        handover.setAcceptedPackages(pkgCount);
        handover.setMissingPackages(0);
        handover.setDamagedPackages(0);
        handover.setNote(inspectionNote != null ? inspectionNote : "Đã nhận hàng hoàn vào khu cách ly hàng lỗi");
        handover.setSignedAt(now);
        handoverRecordRepository.save(handover);

        // Ghi StockMovements vào khu cách ly (RETURN_TO_QUARANTINE), tuyệt đối không cộng lại available_qty
        List<OrderItemEntity> orderItems = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        for (OrderItemEntity item : orderItems) {
            StockMovementEntity quarantineMovement = new StockMovementEntity();
            quarantineMovement.setVariantId(item.getVariantId());
            quarantineMovement.setOrderId(order.getId());
            quarantineMovement.setMovementType("RETURN_TO_QUARANTINE");
            quarantineMovement.setQuantity(item.getQuantity());
            quarantineMovement.setCreatedBy(warehouseUserId);
            quarantineMovement.setCreatedAt(now);
            stockMovementRepository.save(quarantineMovement);
        }

        returnReq.setStatus(ReturnStatus.WAREHOUSE_RECEIVED);
        returnReq.setWarehouseInspectionNote(inspectionNote);
        returnRequestRepository.save(returnReq);

        RefundEntity refund = new RefundEntity();
        refund.setOrderId(order.getId());
        refund.setReturnRequestId(returnReq.getId());
        refund.setRefundReason("ORDER_ISSUE_RETURN");
        refund.setAmount(order.getTotalAmount());
        refund.setStatus("PENDING_APPROVAL");
        refund.setCreatedAt(now);
        return refundRepository.save(refund);
    }

    /**
     * AT-11: Admin phê duyệt và hoàn tất hoàn tiền.
     * - Kiểm tra nếu hoàn tiền do sự cố hàng hóa (ORDER_ISSUE_RETURN), yêu cầu thu hồi phải đã được kho kiểm nhận
     *   (WAREHOUSE_RECEIVED) hoặc được duyệt đặc cách không thể thu hồi (UNRECOVERABLE_WAIVED).
     * - Nếu đơn hàng trước đó đã hoàn tất và đã tính hoa hồng cho đại lý, tự động ghi 1 dòng
     *   bút toán âm (DEBIT_REFUND_REVERSAL) tương ứng vào commission_ledger.
     */
    @Transactional
    public RefundEntity approveAndCompleteRefundByAdmin(Long adminUserId, Long refundId) {
        OffsetDateTime now = timeProvider.now();
        RefundEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu hoàn tiền"));
        OrderEntity order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if ("ORDER_ISSUE_RETURN".equalsIgnoreCase(refund.getRefundReason()) && refund.getReturnRequestId() != null) {
            ReturnRequestEntity returnReq = returnRequestRepository.findById(refund.getReturnRequestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ thu hồi hàng"));
            if (returnReq.getStatus() != ReturnStatus.WAREHOUSE_RECEIVED
                    && returnReq.getStatus() != ReturnStatus.UNRECOVERABLE_WAIVED) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATE_TRANSITION,
                        "Hàng hoàn phải được Kho trung tâm kiểm nhận vào khu cách ly trước khi duyệt hoàn tiền"
                );
            }
            returnReq.setStatus(ReturnStatus.CLOSED);
            returnRequestRepository.save(returnReq);

            issueRepository.findById(returnReq.getIssueId()).ifPresent(issue -> {
                issue.setStatus("RESOLVED");
                issueRepository.save(issue);
            });
        }

        refund.setApprovedBy(adminUserId);
        refund.setStatus("COMPLETED");
        refund.setProviderRef("VNP-REFUND-" + refund.getId());
        refund.setCompletedAt(now);
        refund = refundRepository.save(refund);

        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.setHasOpenIssue(false);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        // AT-11: Tự động sinh bút toán âm thu hồi hoa hồng (DEBIT_REFUND_REVERSAL) nếu đơn đã từng ghi nhận hoa hồng
        commissionService.recordDebitRefundReversalIfCommissioned(order, now);

        return refund;
    }

    public record ReportIssueRequest(
            Long orderId,
            String issueType,
            String description,
            String evidenceUrls
    ) {
    }

    public record ReportedIssueResult(
            Long issueId,
            Long returnRequestId,
            Long orderId,
            Boolean hasOpenIssue
    ) {
    }
}
