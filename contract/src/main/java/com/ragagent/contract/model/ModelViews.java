package com.ragagent.contract.model;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;

/**
 * 模型**视图 → 运行行视图**的投影（契约面内映射，**不吃实体**）。
 *
 * <p>与旧 L1 {@code common.model.ModelViews#of(实体)} 的字段搬运逐字一致
 * （id/name/type/source/status/isDefault/parameters + null 透传）；实体的
 * "parameters 空归一" 已在 {@code ModelCatalogAdapter} 造视图时完成，
 * 故本处直接透传。</p>
 */
public final class ModelViews {

    private ModelViews() {
    }

    /** 视图 → 运行行视图（null 透传）。 */
    public static ModelRowView rowView(ModelView m) {
        if (m == null) {
            return null;
        }
        return new ModelRowView(m.getId(), m.getName(), m.getType(), m.getSource(),
                m.getStatus(), m.isIsDefault(), m.getParameters());
    }
}
