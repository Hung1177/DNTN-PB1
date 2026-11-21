package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverItemRepository extends JpaRepository<HandoverItemEntity, Long> {
    List<HandoverItemEntity> findByHandoverRecordIdOrderByIdAsc(Long handoverRecordId);
}
