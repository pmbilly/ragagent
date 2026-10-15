package com.ragagent.model.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.common.model.ModelCatalogPort;
import com.ragagent.model.domain.Model;
import com.ragagent.model.service.ModelRowViews;
import com.ragagent.model.service.ModelService;
import com.ragagent.model.mapper.ModelMapper;

/**
 * {@link ModelCatalogPort} 的 model 侧实现：行读取与视图成形都发生在属主域。
 *
 * <p>实体 → 载荷视图的转换见 {@link ModelRowViews}（service 包，保持 api → service 单向）。</p>
 */
@Component
public class ModelCatalogAdapter implements ModelCatalogPort {

    private final ModelService modelService;
    private final ModelMapper modelMapper;

    public ModelCatalogAdapter(ModelService modelService, ModelMapper modelMapper) {
        this.modelService = modelService;
        this.modelMapper = modelMapper;
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

    @Override
    public java.util.Optional<ModelRowView> findModelOrEmpty(String modelId) {
        try {
            return java.util.Optional.ofNullable(ModelRowViews.of(modelService.getModelByID(modelId)));
        } catch (ModelService.ModelNotFoundException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public ModelRowView findModelVisible(long tenantId, String modelId) {
        return ModelRowViews.of(modelService.getByIdVisible(tenantId, modelId));
    }

    @Override
    public ModelRowView findModelRow(long tenantId, String modelId) {
        Model row = modelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Model>()
                .eq(Model::getId, modelId)
                .eq(Model::getTenantId, tenantId)
                .last("LIMIT 1"));
        return ModelRowViews.of(row);
    }

}
