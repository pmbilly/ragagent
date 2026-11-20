package com.ragagent.contract.model;

import java.util.List;

/**
 * 文本嵌入的跨域能力端口：按模型事实（base_url / api_key / 模型名）调用嵌入服务，
 * 返回与输入等长的向量列表。
 *
 * <p>消费方（检索、记忆、知识）只认 {@link ModelFacts}；实现由 {@code model-runtime} 提供
 * （按模型 id 装配 provider-aware 客户端，与问答/索引同一条路径）。</p>
 */
public interface EmbeddingPort {

    /**
     * 批量嵌入。
     *
     * @param model 模型事实（调用方已按需解析；不可为 null）
     * @param texts 待嵌入文本
     * @return 与 {@code texts} 等长的向量列表（实现负责错误语义）
     */
    List<float[]> embed(ModelFacts model, List<String> texts);
}
