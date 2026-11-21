package vn.haui.o2ofood.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * UC-C07: Dịch vụ truy xuất tài liệu chính sách có đánh phiên bản (Policy RAG Service)
 * từ bảng knowledge_documents để trả lời chính xác về giờ chốt 23:59, giữ tồn 30 phút,
 * giờ mở nhận hàng 16:00 D+1, tự hoàn tất 00:00 D+2, quy tắc tự hủy và hoàn tiền VNPAY.
 */
@Service
public class PolicyRagService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public PolicyRagService(KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Transactional(readOnly = true)
    public RagAnswerResult answerPolicyQuestion(String userQuestion) {
        String normalizedQuestion = userQuestion != null ? userQuestion.toLowerCase(Locale.ROOT) : "";
        List<KnowledgeDocumentEntity> activeDocs = knowledgeDocumentRepository.findByIsActiveTrueOrderByIdAsc();

        List<ScoredDoc> scoredDocs = new ArrayList<>();
        for (KnowledgeDocumentEntity doc : activeDocs) {
            int score = scoreDocumentRelevance(normalizedQuestion, doc);
            if (score > 0) {
                scoredDocs.add(new ScoredDoc(doc, score));
            }
        }
        scoredDocs.sort(Comparator.comparingInt(ScoredDoc::score).reversed());

        if (scoredDocs.isEmpty()) {
            return new RagAnswerResult(
                    false,
                    "Hiện tại hệ thống chưa có thông tin chính thức trong tài liệu chính sách cho câu hỏi này. "
                            + "Quý khách vui lòng liên hệ bộ phận Chăm sóc khách hàng hoặc Điều phối viên để được hỗ trợ chi tiết.",
                    List.of()
            );
        }

        List<CitedPolicyDoc> citations = scoredDocs.stream()
                .limit(2)
                .map(sd -> new CitedPolicyDoc(sd.doc().getDocCode(), sd.doc().getVersionTag(), sd.doc().getTitle()))
                .toList();

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append("Dựa trên tài liệu chính sách chính thức của hệ thống:\n\n");
        for (int i = 0; i < Math.min(2, scoredDocs.size()); i++) {
            KnowledgeDocumentEntity doc = scoredDocs.get(i).doc();
            answerBuilder.append("**[").append(doc.getDocCode()).append(" - ").append(doc.getVersionTag()).append("] ")
                    .append(doc.getTitle()).append("**\n")
                    .append(doc.getContentMarkdown()).append("\n\n");
        }

        return new RagAnswerResult(true, answerBuilder.toString().trim(), citations);
    }

    private int scoreDocumentRelevance(String q, KnowledgeDocumentEntity doc) {
        String haystack = (doc.getTitle() + " " + doc.getContentMarkdown() + " " + doc.getDocCode())
                .toLowerCase(Locale.ROOT);
        int score = 0;

        String[] policyKeywords = {
                "giờ chốt", "23:59", "00:00", "16:00", "08:00", "30 phút", "giữ tồn", "giữ đơn",
                "nhận hàng", "thanh toán", "vnpay", "cod", "tiền mặt", "hủy", "hoàn tiền",
                "sự cố", "dập hỏng", "thiếu", "fefo", "hạn sử dụng", "đóng gói", "gói cố định",
                "quá hạn", "chưa lấy", "đại lý"
        };

        for (String kw : policyKeywords) {
            if (q.contains(kw) && haystack.contains(kw)) {
                score += 5;
            }
        }

        String[] tokens = q.split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 3 && haystack.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private record ScoredDoc(KnowledgeDocumentEntity doc, int score) {
    }

    public record CitedPolicyDoc(
            String docCode,
            String versionTag,
            String title
    ) {
    }

    public record RagAnswerResult(
            boolean inScope,
            String answerMarkdown,
            List<CitedPolicyDoc> citedDocuments
    ) {
    }
}
