package com.ragagent.model.service;

import com.ragagent.contract.error.BizException;
import com.ragagent.modelruntime.application.ChatConfigs;
import com.ragagent.embedding.EmbedderConfig;
import com.ragagent.llm.domain.ChatConfig;
import com.ragagent.llm.provider.Config;
import com.ragagent.llm.provider.ProviderName;
import com.ragagent.llm.provider.ProviderRegistry;
import com.ragagent.common.model.ModelCatalogPort.ModelRowView;
import com.ragagent.rerank.RerankerConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ragagent.retrieval.vlm.VlmClient.VlmConfig;
import com.ragagent.rerank.RerankerConfigs;

/**
 * 「模型行 → 各能力运行时配置」的映射（原散在 5 个能力层配置类里的 {@code fromModel} 静态工厂）。
 *
 * <p><b>为什么集中到本域</b>：映射的输入是模型行载荷（{@code ModelCatalogPort.ModelRowView}），输出才是能力层配置。
 * 原先工厂写在配置类上，等于让 embedding/rerank/llm/retrieval 反向 import {@code model.domain}
 * （{@code embedding ⇄ model}、{@code llm ⇄ model}、{@code model ⇄ rerank}、{@code model ⇄ retrieval} 四条环的成因）。
 * 配置类字段本就是普通值，不带实体；映射搬到这里后，依赖方向回到"调用方（本域或上层）→ 能力层配置"。
 * 映射体逐字搬移，行为不变。</p>
 */
public final class ModelRuntimeConfigs {

    private ModelRuntimeConfigs() {
    }

public static EmbedderConfig embedderConfig(ModelRowView m, String appId, String appSecret) {
        EmbedderConfig c = new EmbedderConfig();
        if (m == null) {
            return c;
        }
        var p = m.parameters();
        c.setSource(m.source() == null ? "" : m.source());
        c.setBaseUrl(p == null ? "" : p.getBaseUrl());
        c.setApiKey(p == null ? "" : p.getApiKey());
        c.setModelId(m.id() == null ? "" : m.id());
        c.setModelName(m.name() == null ? "" : m.name());
        c.setDimensions(p == null ? 0 : p.getEmbeddingParameters().getDimension());
        c.setSupportsDimensionOverride(p != null && p.getEmbeddingParameters().isSupportsDimensionOverride());
        c.setTruncatePromptTokens(p == null ? 0 : p.getEmbeddingParameters().getTruncatePromptTokens());
        c.setProvider(p == null ? "" : p.getProvider());
        c.setMaxConcurrency(p == null ? 0 : p.getMaxConcurrency());
        c.setExtraConfig(p == null ? null : p.getExtraConfig());
        c.setCustomHeaders(p == null ? null : p.getCustomHeaders());
        c.setAppId(appId);
        c.setAppSecret(appSecret);
        return c;
    }

    /**
     * rerank 运行时配置：实现在 L2 {@code rerank.RerankerConfigs}
     * （本域只做委托——该映射只依赖 L1 载荷与 rerank 配置类型，不需经模型域中转）。
     */
    public static RerankerConfig rerankerConfig(ModelRowView m, String appId, String appSecret) {
        return RerankerConfigs.rerankerConfig(m, appId, appSecret);
    }

    public static Config providerConfig(ModelRowView model) {
        if (model == null) {
            throw BizException.badRequest("model is nil");
        }
        ProviderName providerName = ProviderName.fromValue(model.parameters().getProvider());
        if (providerName == null) {
            // provider 未配置时按 base URL 探测回落
            providerName = ProviderRegistry.detectProvider(model.parameters().getBaseUrl());
        }
        return new Config(
                providerName,
                model.parameters().getBaseUrl(),
                model.parameters().getApiKey(),
                model.name(),
                model.id(),
                null);
    }

    /**
     * chat 运行时配置：实现在 L2 门面 {@code modelruntime.application.ChatConfigs}
     * （本域只做委托——chat 映射不依赖模型域，无需经本域中转）。
     */
    public static ChatConfig chatConfig(ModelRowView m, String appId, String appSecret) {
        return ChatConfigs.chatConfig(m, appId, appSecret);
    }

    public static VlmConfig vlmConfig(ModelRowView m) {
        if (m == null) {
            return null;
        }
        var p = m.parameters();
        String ifType = p == null ? "" : p.getInterfaceType();
        if (ifType == null || ifType.isEmpty()) {
            ifType = "local".equals(m.source()) ? "ollama" : "openai";
        }
        Map<String, String> extra = p == null ? Map.of()
                : new LinkedHashMap<>(p.getExtraConfig() == null ? Map.of() : p.getExtraConfig());
        return new VlmConfig(m.source(), p == null ? "" : p.getBaseUrl(),
                m.name(), p == null ? "" : p.getApiKey(), m.id(), ifType,
                p == null ? "" : p.getProvider(), extra);
    }}
