package com.ragagent.modelruntime.application;

import com.ragagent.common.model.ModelCatalogPort.ModelRowView;
import com.ragagent.common.model.ModelParameters;
import com.ragagent.llm.domain.ChatConfig;

/**
 * 「模型行 → chat 运行时配置」的 L2 映射。
 *
 * <p><b>为什么在 L2</b>：chat 映射只依赖 L1 载荷（{@code ModelRowView} / {@code ModelParameters}）
 * 与同层 {@link ChatConfig}，不依赖模型域；放在本层后，跨域消费方（如 mcp 的使用说明生成）
 * 无需经模型域即可造出 chat 客户端所需配置。其余四个能力（embedding / rerank / provider / vlm）
 * 的映射仍留在模型域的 {@code ModelRuntimeConfigs}（它们同样只依赖 L1 载荷与各能力层配置）。</p>
 *
 * <p><b>归属变更记录</b>：本方法体自 {@code model.service.ModelRuntimeConfigs#chatConfig} 原样搬移
 * （字段搬运与空值口径逐字不变）；搬迁一度尝试把整个 {@code ModelRuntimeConfigs} 移到 L2，
 * 因它与 embedding / rerank / retrieval 形成包环（跨 L2 域的编排不属于任何单一 L2 域）而回退——
 * 只有 chat 这一支是内聚于 {@code llm} 的，故只下沉它。</p>
 */
public final class ChatConfigs {

    private ChatConfigs() {
    }

    /** 模型行为 null ⇒ null（调用方按"无模型"处理）。 */
    public static ChatConfig chatConfig(ModelRowView m, String appId, String appSecret) {
        if (m == null) {
            return null;
        }
        ModelParameters p = m.parameters();
        ChatConfig c = new ChatConfig();
        c.setModelId(m.id());
        c.setApiKey(p == null ? null : p.getApiKey());
        c.setBaseUrl(p == null ? null : p.getBaseUrl());
        c.setModelName(m.name());
        c.setSource(m.source());
        c.setProvider(p == null ? null : p.getProvider());
        c.setMaxConcurrency(p == null ? 0 : p.getMaxConcurrency());
        c.setExtraConfig(p == null ? null : p.getExtraConfig());
        c.setCustomHeaders(p == null ? null : p.getCustomHeaders());
        c.setAppId(appId);
        c.setAppSecret(appSecret);
        return c;
    }
}
