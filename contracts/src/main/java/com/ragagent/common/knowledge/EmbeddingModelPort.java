package com.ragagent.common.knowledge;

import java.util.List;

/**
 * 嵌入模型端口（B98/C2）：wiki 按模型 id 取 embedding 客户端。
 *
 * <p>实现留在 {@code knowledge} 侧（{@code ModelService} 取模型 → embedding 类型闸门 →
 * {@code EmbedderClient.configFrom} → {@code embedBatch}）。wiki 侧只拿一个函数式客户端，
 * 不再接触 {@code EmbedderClient.EmbedConfig}。</p>
 *
 * <p>类型闸门语义不变：非 embedding 模型抛 {@link IllegalStateException}
 * （调用方据此降级为"喂全部目录"）。</p>
 */
public interface EmbeddingModelPort {

    /** 批量嵌入客户端。失败时抛异常（由调用方决定降级）。 */
    @FunctionalInterface
    interface Embedder {
        List<float[]> batchEmbed(List<String> texts) throws Exception;
    }

    Embedder embedderFor(String modelId);
}
