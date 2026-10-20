package com.ragagent.modelruntime.application;

import com.ragagent.contract.model.EmbeddingPort;
import com.ragagent.contract.model.ModelFacts;
import com.ragagent.contract.model.ModelRuntime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link EmbeddingPort} 的运行时实现：按模型 id 走 provider-aware 装配。
 *
 * <p>与问答 / 索引走同一条客户端路径（同一套 provider 参数、SSRF 与响应校验），
 * 不再维护第二条"最小 OpenAI 兼容"客户端。</p>
 */
@Component
public class ModelRuntimeEmbeddingAdapter implements EmbeddingPort {

    private final ModelRuntime runtime;

    public ModelRuntimeEmbeddingAdapter(ModelRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public List<float[]> embed(ModelFacts model, List<String> texts) {
        try {
            return runtime.getEmbeddingModel(model.modelId()).batchEmbed(texts);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }
}
