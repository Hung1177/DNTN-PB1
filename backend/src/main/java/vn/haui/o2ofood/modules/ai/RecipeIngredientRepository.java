package vn.haui.o2ofood.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredientEntity, Long> {
    List<RecipeIngredientEntity> findByRecipeIdOrderByIdAsc(Long recipeId);
}
