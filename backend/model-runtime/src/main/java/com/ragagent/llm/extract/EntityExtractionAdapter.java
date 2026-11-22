package com.ragagent.llm.extract;

import com.ragagent.framework.jsonbind.JsonMappers;
import com.ragagent.llm.api.EntityExtractionPort;
import com.ragagent.llm.api.EntityExtractionResult;
import com.ragagent.llm.api.LlmChatClient;
import com.ragagent.llm.api.PipelineConfig.PromptTemplateStructured;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 实体关系抽取端口的适配器：把契约面的不透明模板还原成平台模板类型，
 * 交给 {@link EntityExtraction.Extractor}，再把内部图形状搬成契约 record。
 *
 * <p>内部形状（{@code EntityExtraction.EntityGraph} 的可变字段）不出模块。</p>
 */
@Component
public class EntityExtractionAdapter implements EntityExtractionPort {

    @Override
    public EntityExtractionResult extract(LlmChatClient chatModel, Object template, String content) {
        PromptTemplateStructured tpl = template instanceof PromptTemplateStructured p ? p
                : JsonMappers.lenient().convertValue(template, PromptTemplateStructured.class);
        EntityExtraction.EntityGraph graph = new EntityExtraction.Extractor(chatModel, tpl)
                .extract(content);
        return new EntityExtractionResult(
                graph.node == null ? List.of() : graph.node,
                graph.relation == null ? List.of() : graph.relation);
    }
}
