package vn.haui.o2ofood.modules.ordering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {
    List<JobRunEntity> findByJobNameOrderByIdDesc(String jobName);
}
