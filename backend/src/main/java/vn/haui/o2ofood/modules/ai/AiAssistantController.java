package vn.haui.o2ofood.modules.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final RecipeToCartService recipeToCartService;
    private final TimeProvider timeProvider;

    public AiAssistantController(AiAssistantService aiAssistantService,
                                 RecipeToCartService recipeToCartService,
                                 TimeProvider timeProvider) {
        this.aiAssistantService = aiAssistantService;
        this.recipeToCartService = recipeToCartService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiAssistantService.ChatAssistantResponse>> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody AiAssistantService.ChatAssistantRequest request) {
        Long authenticatedUserId = principal != null ? principal.getId() : null;
        AiAssistantService.ChatAssistantResponse response = aiAssistantService.chat(authenticatedUserId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, timeProvider.now()));
    }

    @PostMapping("/recipe-suggestions")
    public ResponseEntity<ApiResponse<RecipeToCartService.RecipeSuggestionResponse>> suggestRecipes(
            @RequestBody RecipeToCartService.RecipeSuggestionRequest request) {
        RecipeToCartService.RecipeSuggestionResponse response = recipeToCartService.suggestRecipesAndCart(request);
        return ResponseEntity.ok(ApiResponse.ok("Gợi ý thực đơn và quy đổi gói nguyên liệu thành công", response, timeProvider.now()));
    }
}
