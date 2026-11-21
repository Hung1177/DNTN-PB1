package vn.haui.o2ofood.modules.exception;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
    List<IssueEntity> findByOrderIdOrderByIdAsc(Long orderId);
    List<IssueEntity> findByStatusOrderByCreatedAtDesc(String status);
}
