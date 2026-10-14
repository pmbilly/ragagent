package com.ragagent.model.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.common.model.ModelCatalogPort;
import com.ragagent.model.domain.Model;
import com.ragagent.model.service.ModelRowViews;
import com.ragagent.model.service.ModelService;

/**
 * {@link ModelCatalogPort} 的 model 侧实现：行读取与视图成形都发生在属主域。
 *
 * <p>实体 → 载荷视图的转换见 {@link ModelRowViews}（service 包，保持 api → service 单向）。</p>
 */
@Component
public class ModelCatalogAdapter implements ModelCatalogPort {

    private final ModelService modelService;

    public ModelCatalogAdapter(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public List<ModelRowView> listModels() {
        List<Model> rows = modelService.listModels();
        List<ModelRowView> out = new ArrayList<>();
        if (rows != null) {
            for (Model m : rows) {
                out.add(ModelRowViews.of(m));
            }
        }
        return out;
    }

    @Override
    public ModelRowView findModel(String modelId) {
        return ModelRowViews.of(modelService.getModelByID(modelId));
    }

}
