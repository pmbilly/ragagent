package com.ragagent.embedding;

import com.ragagent.common.model.ModelCatalogPort.ModelRowView;

/**
 * 「模型行 → embedding 运行时配置」的 L2 映射。
 *
 * <p><b>为什么在 {@code embedding} 包（而不是 {@code modelruntime}）</b>：本映射只依赖 L1 载荷
 * （{@code ModelRowView}）与同包 {@link EmbedderConfig}；放本包保证不产生任何跨包依赖——
 * {@code modelruntime} 在 engine 内层级偏低（embedding / rerank / retrieval 已反向依赖它），
 * 把持有这些返回类型的映射放进它才会成环（㉔/㊱/㊲ 三次实测）。</p>
 *
 * <p><b>归属变更记录</b>：方法体自 {@code model.service.ModelRuntimeConfigs#embedderConfig} 原样搬移
 * （字段搬运与空值口径逐字不变），跨域消费方（初始化连通性测试等）据此免于 import 模型域。</p>
 */
public final class EmbedderConfigs {

    private EmbedderConfigs() {
    }

    /** 模型行 null ⇒ 空配置（非 null；与域实现同口径）。 */
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
}
