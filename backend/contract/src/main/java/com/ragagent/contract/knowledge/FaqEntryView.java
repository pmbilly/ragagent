package com.ragagent.contract.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * FAQ 条目**视图**（契约面载荷）：跨域方（会话的引擎装配面）读 FAQ 库条目所需字段。
 *
 * <p><b>为什么有它</b>：实体/dto `FaqEntry` 属知识域内部（方案 列在 `knowledge/dto.faq/`），
 * 跨域读取只经 {@code com.ragagent.contract.knowledge.KnowledgeCatalogPort#listFaqEntries}；
 * 本视图字段＝**该端点的实际消费用度**（7 个），不镜像域内 dto 的全部字段。</p>
 */
public record FaqEntryView(String chunkId, String knowledgeBaseId, String knowledgeId,
                           String standardQuestion, List<String> similarQuestions,
                           List<String> answers, OffsetDateTime createdAt) {
}
