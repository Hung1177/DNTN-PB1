package vn.haui.o2ofood.modules.commission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.MoneyUtils;
import vn.haui.o2ofood.modules.ordering.OrderEntity;
import vn.haui.o2ofood.security.OwnershipSecurityService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionLedgerRepository commissionLedgerRepository;
    private final CommissionPolicyRepository commissionPolicyRepository;
    private final OwnershipSecurityService ownershipSecurityService;

    public CommissionService(CommissionLedgerRepository commissionLedgerRepository,
                             CommissionPolicyRepository commissionPolicyRepository,
                             OwnershipSecurityService ownershipSecurityService) {
        this.commissionLedgerRepository = commissionLedgerRepository;
        this.commissionPolicyRepository = commissionPolicyRepository;
        this.ownershipSecurityService = ownershipSecurityService;
    }

    /**
     * AT-09 & AT-10: Ghi nhận hoa hồng dương (CREDIT_COMMISSION) đúng 1 lần duy nhất cho 1 đơn hàng hoàn tất.
     * Bất biến (Append-only): Không bao giờ UPDATE hoặc DELETE dòng trong commission_ledger.
     */
    @Transactional
    public Optional<CommissionLedgerEntity> recordCreditCommissionOnce(OrderEntity order, OffsetDateTime now) {
        Optional<CommissionLedgerEntity> existing = commissionLedgerRepository
                .findByOrderIdAndEntryType(order.getId(), CommissionEntryType.CREDIT_COMMISSION);
        if (existing.isPresent()) {
            return existing;
        }

        CommissionPolicyEntity policy = commissionPolicyRepository.findById(order.getCommissionPolicyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chính sách hoa hồng"));

        BigDecimal commissionAmount = calculateCommissionAmount(order.getSubtotalAmount(), policy);

        CommissionLedgerEntity entry = new CommissionLedgerEntity();
        entry.setPickupPointId(order.getPickupPointId());
        entry.setOrderId(order.getId());
        entry.setPolicyId(policy.getId());
        entry.setEntryType(CommissionEntryType.CREDIT_COMMISSION);
        entry.setAmount(commissionAmount);
        entry.setNote("Hoa hồng hoàn tất đơn " + order.getOrderCode() + " (" + order.getCompletionType() + ")");
        entry.setCreatedAt(now);

        try {
            return Optional.of(commissionLedgerRepository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException ex) {
            log.info("Đã chặn ghi trùng CREDIT_COMMISSION cho orderId={}", order.getId());
            return commissionLedgerRepository.findByOrderIdAndEntryType(order.getId(), CommissionEntryType.CREDIT_COMMISSION);
        }
    }

    /**
     * AT-11: Khi hoàn tiền cho đơn hàng đã hoàn tất và đã ghi nhận hoa hồng trước đó,
     * tự động ghi thêm 1 dòng bút toán âm (DEBIT_REFUND_REVERSAL) tương ứng vào commission_ledger.
     */
    @Transactional
    public Optional<CommissionLedgerEntity> recordDebitRefundReversalIfCommissioned(OrderEntity order,
                                                                                    OffsetDateTime now) {
        Optional<CommissionLedgerEntity> creditOpt = commissionLedgerRepository
                .findByOrderIdAndEntryType(order.getId(), CommissionEntryType.CREDIT_COMMISSION);
        if (creditOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<CommissionLedgerEntity> existingDebit = commissionLedgerRepository
                .findByOrderIdAndEntryType(order.getId(), CommissionEntryType.DEBIT_REFUND_REVERSAL);
        if (existingDebit.isPresent()) {
            return existingDebit;
        }

        CommissionLedgerEntity creditEntry = creditOpt.get();
        BigDecimal debitAmount = MoneyUtils.normalize(creditEntry.getAmount().negate());

        CommissionLedgerEntity reversal = new CommissionLedgerEntity();
        reversal.setPickupPointId(order.getPickupPointId());
        reversal.setOrderId(order.getId());
        reversal.setPolicyId(creditEntry.getPolicyId());
        reversal.setEntryType(CommissionEntryType.DEBIT_REFUND_REVERSAL);
        reversal.setAmount(debitAmount);
        reversal.setNote("Thu hồi hoa hồng do hoàn tiền đơn " + order.getOrderCode());
        reversal.setCreatedAt(now);

        return Optional.of(commissionLedgerRepository.saveAndFlush(reversal));
    }

    @Transactional(readOnly = true)
    public AgentCommissionStatementDto getStatementForPickupPoint(Long pickupPointId) {
        if (!ownershipSecurityService.isAssignedToPickupPoint(pickupPointId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Bạn không được phân công quản lý điểm nhận hàng này");
        }
        List<CommissionLedgerEntity> entries = commissionLedgerRepository
                .findByPickupPointIdOrderByCreatedAtDescIdDesc(pickupPointId);
        BigDecimal netBalance = MoneyUtils.normalize(
                commissionLedgerRepository.calculateNetCommissionBalance(pickupPointId)
        );
        return new AgentCommissionStatementDto(pickupPointId, netBalance, entries);
    }

    private BigDecimal calculateCommissionAmount(BigDecimal netMerchandiseAmount, CommissionPolicyEntity policy) {
        if ("FIXED_PER_ORDER".equalsIgnoreCase(policy.getCalcType())) {
            return MoneyUtils.normalize(policy.getFixedAmount());
        }
        BigDecimal rate = policy.getPercentageRate() != null ? policy.getPercentageRate() : BigDecimal.ZERO;
        return netMerchandiseAmount.multiply(rate)
                .divide(BigDecimal.valueOf(100), MoneyUtils.CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    public record AgentCommissionStatementDto(
            Long pickupPointId,
            BigDecimal netBalance,
            List<CommissionLedgerEntity> entries
    ) {
    }
}
