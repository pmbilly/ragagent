package com.ragagent.rerank;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelParameters;

/**
 * 「模型行 → rerank 运行时配置」的 L2 映射。
 *
 * <p><b>为什么在 {@code rerank} 包（而不是 {@code modelruntime}）</b>：本映射只依赖 L1 载荷
 * （{@code ModelRowView} / {@code ModelParameters}）与同包 {@link RerankerConfig}；放本包可保证
 * <b>不产生任何跨包依赖</b>——{@code modelruntime} 在 engine 内层级偏低（embedding / rerank /
 * retrieval 已反向依赖它），把持有这些返回类型的映射放进它才会成环（㉔/㊱ 两次实测）。</p>
 *
 * <p><b>归属变更记录</b>：方法体自 {@code model.service.ModelRuntimeConfigs#rerankerConfig} 原样搬移
 * （字段搬运与空值口径逐字不变），跨域消费方（会话装配）据此免于 import 模型域。</p>
 */
public final class RerankerConfigs {

    private RerankerConfigs() {
    }

    /** 模型行 null ⇒ null（调用方按"无模型"处理）。 */
    public static RerankerConfig rerankerConfig(ModelRowView m, String appId, String appSecret) {
        if (m == null) {
            return null;
        }
        RerankerConfig c = new RerankerConfig();
        ModelParameters p = m.parameters();
        c.setModelId(m.id() == null ? "" : m.id());
        c.setApiKey(p == null ? "" : p.getApiKey());
        c.setBaseUrl(p == null ? "" : p.getBaseUrl());
        c.setModelName(m.name() == null ? "" : m.name());
        c.setSource(m.source() == null ? "" : m.source());
        c.setProvider(p == null ? "" : p.getProvider());
        c.setExtraConfig(p == null ? null : p.getExtraConfig());
        c.setCustomHeaders(p == null ? null : p.getCustomHeaders());
        c.setAppId(appId);
        c.setAppSecret(appSecret);
        return c;
    }
}
