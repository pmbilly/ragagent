package com.ragagent.knowledge.dto.faq;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FaqDeleteRequest(
        @NotEmpty(message = "ids: 不能为空")
        List<Long> ids) {
}
