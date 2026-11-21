package vn.haui.o2ofood.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.MoneyUtils;
import vn.haui.o2ofood.modules.catalog.ProductEntity;
import vn.haui.o2ofood.modules.catalog.ProductRepository;
import vn.haui.o2ofood.modules.catalog.ProductVariantEntity;
import vn.haui.o2ofood.modules.catalog.ProductVariantRepository;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceEntity;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceRepository;
import vn.haui.o2ofood.modules.cycle.OrderCycleRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-C08 & AT-12: Dịch vụ Gợi ý thực đơn & Quy đổi giỏ nguyên liệu theo quy cách đóng gói cố định (RecipeToCartService).
 * - Hoàn toàn xác định (Deterministic Backend Logic) cho việc quy đổi định lượng ra số gói, kiểm tra tồn kho và tính tổng tiền.
 * - Giá bán luôn lấy trực tiếp từ bảng product_variants của Backend, miễn nhiễm 100% với Prompt Injection ép giá (AT-12).
 * - Công thức làm tròn gói cố định: packages = CEIL(needed_qty / pack_weight_grams).
 */
@Service
public class RecipeToCartService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OrderCycleRepository orderCycleRepository;

    public RecipeToCartService(RecipeRepository recipeRepository,
                               RecipeIngredientRepository recipeIngredientRepository,
                               ProductRepository productRepository,
                               ProductVariantRepository productVariantRepository,
                               InventoryBalanceRepository inventoryBalanceRepository,
                               OrderCycleRepository orderCycleRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.orderCycleRepository = orderCycleRepository;
    }

    @Transactional(readOnly = true)
    public RecipeSuggestionResponse suggestRecipesAndCart(RecipeSuggestionRequest request) {
        orderCycleRepository.findById(request.cycleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao hàng"));

        int targetServings = request.servings() > 0 ? request.servings() : 2;
        BigDecimal budget = MoneyUtils.normalize(request.budgetVnd() != null ? request.budgetVnd() : MoneyUtils.of(150000));
        Set<Long> pantryProductIds = request.pantryProductIds() != null ? new HashSet<>(request.pantryProductIds()) : Set.of();
        List<String> normalizedAllergies = request.allergies() != null
                ? request.allergies().stream().filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT).trim()).toList()
                : List.of();
        String dietaryFilter = request.dietaryPreference() != null ? request.dietaryPreference().toLowerCase(Locale.ROOT).trim() : "";

        Map<Long, ProductEntity> productsById = productRepository.findByIsActiveTrueOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        Map<Long, List<ProductVariantEntity>> variantsByProductId = productVariantRepository.findByIsActiveTrueOrderByIdAsc()
                .stream()
                .collect(Collectors.groupingBy(ProductVariantEntity::getProductId));

        Map<Long, InventoryBalanceEntity> balancesByVariantId = inventoryBalanceRepository
                .findByCycleIdOrderByVariantIdAsc(request.cycleId())
                .stream()
                .collect(Collectors.toMap(InventoryBalanceEntity::getVariantId, Function.identity()));

        List<RecipeEntity> activeRecipes = recipeRepository.findByIsActiveTrueOrderByIdAsc();
        List<SuggestedRecipePlanDto> withinBudgetPlans = new ArrayList<>();
        List<SuggestedRecipePlanDto> overBudgetDueToPackSizePlans = new ArrayList<>();

        for (RecipeEntity recipe : activeRecipes) {
            if (!dietaryFilter.isEmpty()) {
                String tags = recipe.getDietaryTags() != null ? recipe.getDietaryTags().toLowerCase(Locale.ROOT) : "";
                if (!tags.contains(dietaryFilter)) {
                    continue;
                }
            }

            List<RecipeIngredientEntity> ingredients = recipeIngredientRepository.findByRecipeIdOrderByIdAsc(recipe.getId());
            if (ingredients.isEmpty()) {
                continue;
            }

            boolean hasAllergen = false;
            boolean outOfStock = false;
            List<SuggestedCartItemDto> cartItems = new ArrayList<>();
            BigDecimal recipeTotalAmount = MoneyUtils.ZERO;

            for (RecipeIngredientEntity ing : ingredients) {
                ProductEntity product = productsById.get(ing.getProductId());
                if (product == null || !Boolean.TRUE.equals(product.getIsActive())) {
                    outOfStock = true;
                    break;
                }

                if (matchesAllergy(product, ing, normalizedAllergies)) {
                    hasAllergen = true;
                    break;
                }

                // Trừ nguyên liệu khách đã có sẵn tại nhà
                if (pantryProductIds.contains(product.getId())) {
                    continue;
                }

                List<ProductVariantEntity> variants = variantsByProductId.getOrDefault(product.getId(), List.of());
                if (variants.isEmpty()) {
                    outOfStock = true;
                    break;
                }
                // Chọn quy cách gói nhỏ nhất đang mở bán của sản phẩm
                ProductVariantEntity chosenVariant = variants.stream()
                        .min(Comparator.comparingInt(ProductVariantEntity::getPackWeightGrams))
                        .orElseThrow();

                int scaledNeededGrams = (int) Math.ceil(
                        (double) ing.getRequiredWeightGrams() * targetServings / Math.max(recipe.getServings(), 1)
                );
                int packWeightGrams = chosenVariant.getPackWeightGrams();
                int packagesNeeded = (int) Math.ceil((double) scaledNeededGrams / packWeightGrams);
                int totalPurchasedGrams = packagesNeeded * packWeightGrams;
                int leftoverGrams = totalPurchasedGrams - scaledNeededGrams;

                InventoryBalanceEntity balance = balancesByVariantId.get(chosenVariant.getId());
                int availableQty = balance != null ? balance.getAvailableQty() : 0;
                if (availableQty < packagesNeeded) {
                    outOfStock = true;
                    break;
                }

                // AT-12: Giá bán luôn lấy trực tiếp từ chosenVariant.getUnitPrice() trong DB
                BigDecimal officialUnitPrice = MoneyUtils.normalize(chosenVariant.getUnitPrice());
                BigDecimal lineTotal = MoneyUtils.multiply(officialUnitPrice, packagesNeeded);
                recipeTotalAmount = MoneyUtils.normalize(recipeTotalAmount.add(lineTotal));

                cartItems.add(new SuggestedCartItemDto(
                        product.getId(),
                        chosenVariant.getId(),
                        chosenVariant.getSku(),
                        product.getName(),
                        chosenVariant.getPackName(),
                        packWeightGrams,
                        scaledNeededGrams,
                        packagesNeeded,
                        leftoverGrams,
                        officialUnitPrice,
                        lineTotal,
                        availableQty
                ));
            }

            if (hasAllergen || outOfStock || cartItems.isEmpty()) {
                continue;
            }

            boolean withinBudget = recipeTotalAmount.compareTo(budget) <= 0;
            BigDecimal budgetDiff = MoneyUtils.normalize(recipeTotalAmount.subtract(budget));
            String explanation = buildDeterministicExplanation(recipe, targetServings, budget, recipeTotalAmount, withinBudget, cartItems);

            SuggestedRecipePlanDto plan = new SuggestedRecipePlanDto(
                    recipe.getId(),
                    recipe.getName(),
                    targetServings,
                    recipe.getCookingTimeMinutes(),
                    recipe.getInstructions(),
                    withinBudget,
                    budget,
                    recipeTotalAmount,
                    withinBudget ? MoneyUtils.ZERO : budgetDiff,
                    explanation,
                    cartItems
            );

            if (withinBudget) {
                withinBudgetPlans.add(plan);
            } else {
                overBudgetDueToPackSizePlans.add(plan);
            }
        }

        List<SuggestedRecipePlanDto> finalSuggestions = new ArrayList<>(withinBudgetPlans);
        if (finalSuggestions.size() < 5) {
            overBudgetDueToPackSizePlans.sort(Comparator.comparing(SuggestedRecipePlanDto::totalCartAmount));
            for (SuggestedRecipePlanDto overPlan : overBudgetDueToPackSizePlans) {
                if (finalSuggestions.size() >= 5) {
                    break;
                }
                finalSuggestions.add(overPlan);
            }
        }

        return new RecipeSuggestionResponse(
                request.cycleId(),
                targetServings,
                budget,
                finalSuggestions
        );
    }

    private boolean matchesAllergy(ProductEntity product,
                                   RecipeIngredientEntity ingredient,
                                   List<String> allergies) {
        if (allergies.isEmpty()) {
            return false;
        }
        String allergenHaystack = ((product.getAllergenTags() != null ? product.getAllergenTags() : "")
                + " " + product.getName()
                + " " + ingredient.getIngredientName()).toLowerCase(Locale.ROOT);
        for (String allergy : allergies) {
            if (!allergy.isBlank() && allergenHaystack.contains(allergy)) {
                return true;
            }
        }
        return false;
    }

    private String buildDeterministicExplanation(RecipeEntity recipe,
                                                 int targetServings,
                                                 BigDecimal budget,
                                                 BigDecimal totalAmount,
                                                 boolean withinBudget,
                                                 List<SuggestedCartItemDto> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("Món **").append(recipe.getName()).append("** phù hợp cho ").append(targetServings).append(" người ăn. ");
        if (withinBudget) {
            sb.append("Tổng tiền giỏ nguyên liệu theo quy cách đóng gói cố định là **")
                    .append(totalAmount).append("đ** (nằm trong ngân sách ").append(budget).append("đ).");
        } else {
            sb.append("Do quy cách đóng gói cố định tại kho (làm tròn lên theo gói nguyên), tổng tiền là **")
                    .append(totalAmount).append("đ**, vượt ngân sách ").append(budget).append("đ một khoản **")
                    .append(totalAmount.subtract(budget)).append("đ** nhưng phần nguyên liệu dư có thể bảo quản dùng cho bữa tiếp theo.");
        }
        return sb.toString();
    }

    public record RecipeSuggestionRequest(
            Long cycleId,
            int servings,
            BigDecimal budgetVnd,
            List<Long> pantryProductIds,
            List<String> allergies,
            String dietaryPreference,
            String userPrompt
    ) {
    }

    public record SuggestedCartItemDto(
            Long productId,
            Long variantId,
            String sku,
            String productName,
            String packName,
            int packWeightGrams,
            int requiredWeightGrams,
            int packagesToBuy,
            int leftoverWeightGrams,
            BigDecimal officialUnitPrice,
            BigDecimal lineTotal,
            int availableStockInCycle
    ) {
    }

    public record SuggestedRecipePlanDto(
            Long recipeId,
            String recipeName,
            int servings,
            int cookingTimeMinutes,
            String instructions,
            boolean withinBudget,
            BigDecimal requestedBudget,
            BigDecimal totalCartAmount,
            BigDecimal budgetExcessDueToFixedPack,
            String aiExplanation,
            List<SuggestedCartItemDto> cartItems
    ) {
    }

    public record RecipeSuggestionResponse(
            Long cycleId,
            int servings,
            BigDecimal budgetVnd,
            List<SuggestedRecipePlanDto> suggestions
    ) {
    }
}
