package com.ragagent.knowledge.dto.faq;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AddSimilarQuestionsRequest(
        @NotEmpty(message = "similarQuestions: 不能为空")
        List<String> similarQuestions) {
}
