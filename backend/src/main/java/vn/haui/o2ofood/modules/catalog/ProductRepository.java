package vn.haui.o2ofood.modules.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByIsActiveTrueOrderByIdAsc();
}
