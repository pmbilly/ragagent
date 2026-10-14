package com.ragagent.model.service;

import com.ragagent.common.model.ModelCatalogPort.ModelRowView;
import com.ragagent.model.domain.Model;

/**
 * 模型实体 → L1 载荷视图（{@link ModelRowView}）的转换。
 *
 * <p><b>为什么在 service 包</b>：视图成形被 {@code model.api.ModelCatalogAdapter}（端口实现）与
 * 域内调用方（运行时组装、调试端点）共用；放在 service 层可保持 {@code api → service} 单向，
 * 避免 api 与 service 形成包级环。</p>
 */
public final class ModelRowViews {

    private ModelRowViews() {
    }

    /** 实体 → 载荷视图（null 透传）。 */
    public static ModelRowView of(Model m) {
        if (m == null) {
            return null;
        }
        return new ModelRowView(m.getId(), m.getName(), m.getType(), m.getSource(),
                m.getStatus(), m.isIsDefault(), m.getParameters());
    }
}
