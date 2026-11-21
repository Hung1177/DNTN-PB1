package vn.haui.o2ofood.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.modules.ordering.OrderService;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ điều phối AI Chatbot (Mục 1.7, UC-C07, UC-C08, AT-12):
 * - Ranh giới an toàn AI (AI Safety Boundary):
 *   1. Tuyệt đối không sinh SQL trực tiếp, không tự ý xác nhận thanh toán, hủy đơn, hoàn tiền hay sửa giá.
 *   2. Khi khách hỏi về mã đơn hàng (ORD-...) hoặc danh sách đơn của mình -> kích hoạt Tool Calling
 *      qua OrderLookupToolService với authenticatedUserId của phiên JWT.
 *   3. Khi khách hỏi chính sách -> truy xuất PolicyRagService từ bảng knowledge_documents.
 */
@Service
public class AiAssistantService {

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(ORD-[0-9]{8}-[0-9A-Za-z-]+)");

    private final PolicyRagService policyRagService;
    private final OrderLookupToolService orderLookupToolService;
    private final RecipeToCartService recipeToCartService;

    public AiAssistantService(PolicyRagService policyRagService,
                              OrderLookupToolService orderLookupToolService,
                              RecipeToCartService recipeToCartService) {
        this.policyRagService = policyRagService;
        this.orderLookupToolService = orderLookupToolService;
        this.recipeToCartService = recipeToCartService;
    }

    @Transactional(readOnly = true)
    public ChatAssistantResponse chat(Long authenticatedUserId, ChatAssistantRequest request) {
        String message = request.message() != null ? request.message().trim() : "";
        String lower = message.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra nếu câu hỏi chứa mã đơn hàng cụ thể (ORD-...) -> Kích hoạt Tool GET_ORDER_DETAIL
        Matcher orderCodeMatcher = ORDER_CODE_PATTERN.matcher(message);
        if (orderCodeMatcher.find()) {
            String orderCode = orderCodeMatcher.group(1);
            OrderService.OrderDetailDto detail = orderLookupToolService.getOrderDetailByCode(authenticatedUserId, orderCode);
            String reply = String.format(
                    "Đơn hàng **%s** của quý khách hiện có trạng thái thương mại **%s**, trạng thái giao nhận **%s**, trạng thái thanh toán **%s**, tổng tiền **%sđ** (Hạn chốt: %s, Giờ mở nhận: %s).",
                    detail.orderCode(),
                    detail.orderStatus(),
                    detail.fulfillmentStatus(),
                    detail.paymentStatus(),
                    detail.totalAmount(),
                    detail.cutoffAt(),
                    detail.pickupStartAt()
            );
            return new ChatAssistantResponse(
                    "TOOL_CALL_ORDER_DETAIL",
                    AiToolName.GET_ORDER_DETAIL.name(),
                    reply,
                    detail,
                    List.of()
            );
        }

        // 2. Kiểm tra nếu khách hỏi danh sách đơn hàng của mình -> Kích hoạt Tool GET_CUSTOMER_ORDERS
        if (lower.contains("đơn hàng của tôi") || lower.contains("đơn của tôi") || lower.contains("tra cứu đơn")) {
            List<OrderService.OrderSummaryDto> orders = orderLookupToolService.getCustomerOrders(authenticatedUserId);
            String reply = orders.isEmpty()
                    ? "Hiện tại quý khách chưa có đơn hàng nào trên hệ thống."
                    : "Quý khách hiện có **" + orders.size() + "** đơn hàng. Đơn gần nhất là **" + orders.get(0).orderCode()
                    + "** (Trạng thái: " + orders.get(0).orderStatus() + ").";
            return new ChatAssistantResponse(
                    "TOOL_CALL_CUSTOMER_ORDERS",
                    AiToolName.GET_CUSTOMER_ORDERS.name(),
                    reply,
                    orders,
                    List.of()
            );
        }

        // 3. Chặn các yêu cầu Prompt Injection đòi sửa giá sản phẩm, tự xác nhận thanh toán hoặc sinh SQL
        if (isPromptInjectionPriceOrStateOverride(lower)) {
            return new ChatAssistantResponse(
                    "SAFETY_GUARDRAIL_BLOCKED",
                    null,
                    "Trợ lý AI chỉ hỗ trợ tư vấn chính sách, tra cứu đơn hàng chính chủ và gợi ý thực đơn. "
                            + "Giá bán sản phẩm, trạng thái thanh toán VNPAY và quy tắc hủy/hoàn tiền được kiểm soát cố định bởi hệ thống Backend.",
                    null,
                    List.of()
            );
        }

        // 4. Hỏi đáp chính sách qua Policy RAG Service
        PolicyRagService.RagAnswerResult ragResult = policyRagService.answerPolicyQuestion(message);
        return new ChatAssistantResponse(
                ragResult.inScope() ? "POLICY_RAG" : "OUT_OF_SCOPE",
                null,
                ragResult.answerMarkdown(),
                null,
                ragResult.citedDocuments()
        );
    }

    private boolean isPromptInjectionPriceOrStateOverride(String lowerMessage) {
        return (lowerMessage.contains("ignore previous")
                || lowerMessage.contains("bỏ qua chỉ thị")
                || lowerMessage.contains("sửa giá")
                || lowerMessage.contains("ép giá")
                || lowerMessage.contains("giá 0đ")
                || lowerMessage.contains("giá 1000")
                || lowerMessage.contains("drop table")
                || lowerMessage.contains("update orders"));
    }

    public record ChatAssistantRequest(
            String message
    ) {
    }

    public record ChatAssistantResponse(
            String intentType,
            String executedTool,
            String replyMarkdown,
            Object toolData,
            List<PolicyRagService.CitedPolicyDoc> citedPolicies
    ) {
    }
}
