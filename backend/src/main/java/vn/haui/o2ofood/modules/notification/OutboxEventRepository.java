package vn.haui.o2ofood.modules.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findByAggregateIdOrderByIdAsc(String aggregateId);
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
