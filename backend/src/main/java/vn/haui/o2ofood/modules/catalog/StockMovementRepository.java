package vn.haui.o2ofood.modules.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
    List<StockMovementEntity> findByOrderIdOrderByIdAsc(Long orderId);
    List<StockMovementEntity> findByVariantIdOrderByIdDesc(Long variantId);
}
