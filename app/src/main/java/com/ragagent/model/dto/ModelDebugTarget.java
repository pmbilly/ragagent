package com.ragagent.model.dto;

import com.ragagent.contract.model.ModelParameters;
import com.ragagent.model.domain.Model;

/** 调试面需要的模型字段投影：控制器不再直接持有 {@link Model}。 */
public record ModelDebugTarget(
        String id,
        String name,
        String type,
        String source,
        String status,
        boolean isDefault,
        ModelParameters parameters) {

    public static ModelDebugTarget from(Model model) {
        return new ModelDebugTarget(
                model.getId(),
                model.getName(),
                model.getType(),
                model.getSource(),
                model.getStatus(),
                model.isIsDefault(),
                model.getParameters());
    }
}
