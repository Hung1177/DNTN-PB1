package vn.haui.o2ofood.modules.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<LotEntity, Long> {

    Optional<LotEntity> findByLotCode(String lotCode);

    List<LotEntity> findByVariantIdOrderByExpiryDateAscReceivedAtAscIdAsc(Long variantId);

    /**
     * Mục 4.3 & AT-07: Truy vấn các lô khả dụng theo chuẩn FEFO (First-Expired, First-Out):
     * - status = 'AVAILABLE' (bỏ qua lô QUARANTINED và EXPIRED)
     * - remaining_qty > 0
     * - expiry_date >= minExpiryDate (pickup_date + min_shelf_life_days_at_pickup)
     * - Sắp xếp ưu tiên lô hết hạn sớm nhất: expiry_date ASC, received_at ASC, id ASC
     */
    @Query("""
            SELECT l FROM LotEntity l
            WHERE l.variantId = :variantId
              AND l.status = 'AVAILABLE'
              AND l.remainingQty > 0
              AND l.expiryDate >= :minExpiryDate
            ORDER BY l.expiryDate ASC, l.receivedAt ASC, l.id ASC
            """)
    List<LotEntity> findEligibleLotsForFefo(@Param("variantId") Long variantId,
                                            @Param("minExpiryDate") LocalDate minExpiryDate);
}
