package vn.haui.o2ofood.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {
    List<KnowledgeDocumentEntity> findByIsActiveTrueOrderByIdAsc();
    Optional<KnowledgeDocumentEntity> findByDocCode(String docCode);
}
