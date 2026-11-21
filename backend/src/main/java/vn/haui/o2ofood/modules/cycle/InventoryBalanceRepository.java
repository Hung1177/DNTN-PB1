package vn.haui.o2ofood.modules.cycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalanceEntity, Long> {

    Optional<InventoryBalanceEntity> findByCycleIdAndVariantId(Long cycleId, Long variantId);

    List<InventoryBalanceEntity> findByCycleIdOrderByVariantIdAsc(Long cycleId);

    /**
     * Giải thuật giữ tồn kho chống bán vượt (Anti-Overselling Atomic Conditional Update).
     * Trả về 1 nếu trừ tồn khả dụng thành công; trả về 0 nếu hết hàng (available_qty < reqQty).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalanceEntity ib
            SET ib.availableQty = ib.availableQty - :reqQty,
                ib.reservedQty = ib.reservedQty + :reqQty,
                ib.version = ib.version + 1,
                ib.updatedAt = :now
            WHERE ib.variantId = :variantId
              AND ib.cycleId = :cycleId
              AND ib.availableQty >= :reqQty
            """)
    int reserveStockAtomically(@Param("cycleId") Long cycleId,
                               @Param("variantId") Long variantId,
                               @Param("reqQty") int reqQty,
                               @Param("now") OffsetDateTime now);

    /**
     * Chuyển từ giữ tồn tạm thời (reserved_qty) sang tồn đã cam kết thanh toán (committed_qty)
     * khi nhận được VNPAY IPN hợp lệ trước giờ chốt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalanceEntity ib
            SET ib.reservedQty = ib.reservedQty - :qty,
                ib.committedQty = ib.committedQty + :qty,
                ib.version = ib.version + 1,
                ib.updatedAt = :now
            WHERE ib.variantId = :variantId
              AND ib.cycleId = :cycleId
              AND ib.reservedQty >= :qty
            """)
    int commitReservedStock(@Param("cycleId") Long cycleId,
                            @Param("variantId") Long variantId,
                            @Param("qty") int qty,
                            @Param("now") OffsetDateTime now);

    /**
     * Hoàn trả tồn kho từ reserved_qty về lại available_qty (khi hết hạn 30 phút chờ thanh toán
     * hoặc khách tự hủy đơn ở trạng thái PENDING_PAYMENT).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalanceEntity ib
            SET ib.reservedQty = ib.reservedQty - :qty,
                ib.availableQty = ib.availableQty + :qty,
                ib.version = ib.version + 1,
                ib.updatedAt = :now
            WHERE ib.variantId = :variantId
              AND ib.cycleId = :cycleId
              AND ib.reservedQty >= :qty
            """)
    int releaseReservedStock(@Param("cycleId") Long cycleId,
                             @Param("variantId") Long variantId,
                             @Param("qty") int qty,
                             @Param("now") OffsetDateTime now);

    /**
     * Hoàn trả tồn kho từ committed_qty về lại available_qty (khi khách tự hủy đơn
     * ở trạng thái PAID_WAITING_CUTOFF trước giờ chốt 23:59:59).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalanceEntity ib
            SET ib.committedQty = ib.committedQty - :qty,
                ib.availableQty = ib.availableQty + :qty,
                ib.version = ib.version + 1,
                ib.updatedAt = :now
            WHERE ib.variantId = :variantId
              AND ib.cycleId = :cycleId
              AND ib.committedQty >= :qty
            """)
    int releaseCommittedStock(@Param("cycleId") Long cycleId,
                              @Param("variantId") Long variantId,
                              @Param("qty") int qty,
                              @Param("now") OffsetDateTime now);
}
