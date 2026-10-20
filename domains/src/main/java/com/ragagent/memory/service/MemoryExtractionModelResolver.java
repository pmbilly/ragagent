package com.ragagent.memory.service;

import com.ragagent.contract.memory.MemoryConfig;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.memory.domain.MemoryExtractPayload;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆抽取/归并/话题三处共用的模型解析：显式配置 → 任务携带 → 工作区问答模型。
 *
 * <p>「留空 = 用对话模型」必须解析出一个模型，而不是关掉；三处调用共用同一回落，
 * 否则话题解析器会在每个没挑过模型的工作区上悄悄失去模型层。</p>
 */
final class MemoryExtractionModelResolver {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionModelResolver.class);

    private final MemoryModelResolver modelResolver;

    MemoryExtractionModelResolver(MemoryModelResolver modelResolver) {
        this.modelResolver = modelResolver;
    }

    String extractionModelId(MemoryConfig cfg, MemoryExtractPayload payload) {
        if (cfg != null && !cfg.getExtractModelId().isEmpty()) {
            return cfg.getExtractModelId();
        }
        if (payload != null && !payload.chatModelId().isEmpty()) {
            return payload.chatModelId();
        }
        // 产生这个任务的那一轮并不总是带着回答它所使用的模型——真正的模型是在 QA 管线里
        // 解析的，而且没有被写回消息。回落到工作区自己的问答模型，能把文档承诺的
        // "留空 = 用对话模型"从"记忆悄悄什么都不做"里救回来。
        return workspaceChatModelId();
    }

    /**
     * 给这个工作区挑一个可用的问答模型。
     *
     * <p>选择被记进日志，因为它是一个猜测：没有显式配置抽取模型时，没有任何记录说明
     * 这个工作区希望后台工作用哪个模型，而**悄悄**挑一个只有在事后可见时才可接受。</p>
     */
    String workspaceChatModelId() {
        List<ModelRowView> models;
        try {
            models = modelResolver.listModels();
        } catch (RuntimeException e) {
            log.warn("memory: list models for extraction fallback failed", e);
            return "";
        }
        if (models == null) {
            return "";
        }
        for (ModelRowView model : models) {
            if (model == null || !"KnowledgeQA".equals(model.type())) {
                continue;
            }
            if (!model.status().isEmpty() && !ModelCatalogPort.ACTIVE_STATUS.equals(model.status())) {
                continue;
            }
            log.info("memory: no extraction model configured, using workspace model {}", model.id());
            return model.id();
        }
        return "";
    }
}
