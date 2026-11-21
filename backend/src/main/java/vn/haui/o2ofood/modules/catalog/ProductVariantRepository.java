package vn.haui.o2ofood.modules.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {
    List<ProductVariantEntity> findByIsActiveTrueOrderByIdAsc();
    List<ProductVariantEntity> findByProductIdAndIsActiveTrueOrderByIdAsc(Long productId);
    Optional<ProductVariantEntity> findBySku(String sku);
}
