package com.ragagent.rerank;

import com.ragagent.llm.provider.ProviderName;
import com.ragagent.llm.provider.ProviderRegistry;
import com.ragagent.rerank.provider.AliyunReranker;
import com.ragagent.rerank.provider.JinaReranker;
import com.ragagent.rerank.provider.LkeapReranker;
import com.ragagent.rerank.provider.NvidiaReranker;
import com.ragagent.rerank.provider.OpenAiReranker;
import com.ragagent.rerank.provider.VolcengineReranker;
import com.ragagent.rerank.provider.WeknoraCloudReranker;
import com.ragagent.rerank.provider.ZhipuReranker;

/**
 * reranker 工厂。
 *
 * <p>provider 字段缺省时按 {@link ProviderRegistry#detectProvider} 路由；customHeaders 在
 * 工厂层统一注入。debug/langfuse 装饰器未实现（等价于未启用路径）。</p>
 */
public final class RerankerFactory {

    private RerankerFactory() {
    }

    /** 构造 reranker 并注入 customHeaders。 */
    public static Reranker newReranker(RerankerConfig config) {
        Reranker r = newRerankerInner(config);
        if (r instanceof OpenAiReranker o) {
            o.setCustomHeaders(config.getCustomHeaders());
        } else if (r instanceof AliyunReranker a) {
            a.setCustomHeaders(config.getCustomHeaders());
        } else if (r instanceof ZhipuReranker z) {
            z.setCustomHeaders(config.getCustomHeaders());
        } else if (r instanceof JinaReranker j) {
            j.setCustomHeaders(config.getCustomHeaders());
        } else if (r instanceof NvidiaReranker n) {
            n.setCustomHeaders(config.getCustomHeaders());
        }
        return r;
    }

    /** 按 provider 路由到具体实现。 */
    static Reranker newRerankerInner(RerankerConfig config) {
        ProviderName providerName = ProviderName.fromValue(config.getProvider());
        if (providerName == null) {
            providerName = ProviderRegistry.detectProvider(config.getBaseUrl());
        }
        String name = providerName == null ? "" : providerName.value();
        return switch (name) {
            case "aliyun" -> new AliyunReranker(config);
            case "zhipu" -> new ZhipuReranker(config);
            case "jina" -> new JinaReranker(config);
            case "nvidia" -> new NvidiaReranker(config);
            case "weknoracloud" -> new WeknoraCloudReranker(config);
            case "lkeap" -> new LkeapReranker(config);
            case "volcengine" -> new VolcengineReranker(config);
            default -> new OpenAiReranker(config);
        };
    }
}
