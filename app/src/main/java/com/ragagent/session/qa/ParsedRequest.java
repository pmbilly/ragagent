package com.ragagent.session.qa;

import com.ragagent.session.dto.QaRequests.CreateKnowledgeQARequest;
import com.ragagent.session.service.QaSupport.QaRequestContext;

/** QA 请求解析结果（原为 {@code KnowledgeQaController} 的内嵌 record，阶段 5 随解析器迁出）。 */
public record ParsedRequest(QaRequestContext reqCtx, CreateKnowledgeQARequest request) {
}
