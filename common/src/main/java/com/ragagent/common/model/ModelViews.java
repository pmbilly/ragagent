package com.ragagent.common.model;

import com.ragagent.common.model.ModelCatalogPort.ModelRowView;

/**
 * 模型实体 → L1 载荷视图的投影（实体与视图同在 L1，投影不该住域里）。
 *
 * <p>方法体自模型域 {@code ModelRowViews#of} 原样搬入（字段搬运与 null 透传逐字不变）。</p>
 */
public final class ModelViews {

    private ModelViews() {
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
