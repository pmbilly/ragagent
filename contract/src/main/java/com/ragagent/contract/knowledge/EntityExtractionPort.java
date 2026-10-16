package com.ragagent.contract.knowledge;

import com.ragagent.contract.graph.EntityExtractionResult;
import com.ragagent.contract.model.LlmChatClient;

/**
 * 实体关系抽取端口（业务域 → 模型运行时）。
 *
 * <p><b>R2c-3d 契约化</b>：知识抽取（chunk 后处理）、初始化域的抽取自测、会话管线的
 * extract-entity 插件三处原先都直接 {@code new EntityExtraction.Extractor(...)} 并读其内部
 * 图形状。</p>
 *
 * <p><b>模板是不透明配置载体</b>：它的真实类型在平台面
 * （{@code platform.prompt.PipelineConfig.PromptTemplateStructured}，由 yaml 装载）；
 * 契约层不引平台类型，故按不透明载荷传入，实现侧按其结构解释（非该类型时按 JSON 映射）。</p>
 *
 * <p>失败以运行时异常表达（实现侧原有语义：模型调用/解析失败）。</p>
 */
public interface EntityExtractionPort {

    /** 用给定 chat 模型与抽取模板，从文本抽实体关系。 */
    EntityExtractionResult extract(LlmChatClient chatModel, Object template, String content);
}
