package vn.haui.o2ofood.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<RecipeEntity, Long> {
    List<RecipeEntity> findByIsActiveTrueOrderByIdAsc();
}
