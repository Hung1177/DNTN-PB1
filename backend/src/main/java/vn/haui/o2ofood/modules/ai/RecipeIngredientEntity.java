package vn.haui.o2ofood.modules.ai;

import jakarta.persistence.*;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ingredient_name", nullable = false, length = 120)
    private String ingredientName;

    @Column(name = "required_weight_grams", nullable = false)
    private Integer requiredWeightGrams;

    @Column(name = "is_optional", nullable = false)
    private Boolean isOptional = false;

    public RecipeIngredientEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public void setIngredientName(String ingredientName) {
        this.ingredientName = ingredientName;
    }

    public Integer getRequiredWeightGrams() {
        return requiredWeightGrams;
    }

    public void setRequiredWeightGrams(Integer requiredWeightGrams) {
        this.requiredWeightGrams = requiredWeightGrams;
    }

    public Boolean getIsOptional() {
        return isOptional;
    }

    public void setIsOptional(Boolean optional) {
        isOptional = optional;
    }
}
