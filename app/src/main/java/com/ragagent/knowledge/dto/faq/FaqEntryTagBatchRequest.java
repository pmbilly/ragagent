package com.ragagent.knowledge.dto.faq;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record FaqEntryTagBatchRequest(
        @NotEmpty(message = "updates: 不能为空")
        Map<Long, Long> updates) {
}
