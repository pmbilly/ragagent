package com.ragagent.session.service;

import com.ragagent.contract.agent.ImageDescriberFunc;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.model.VlmPredictor;
import org.springframework.stereotype.Component;

/**
 * 工具结果图片 VLM 描述器的装配：取 VLM 模型行 → 由模型运行时造预测器 → 交引擎。
 *
 * <p>装配链（配置解析 + SSRF 校验 + langfuse 观测 + 命名并发闸门）整段收在
 * {@link ModelRuntime#vlmPredictorFor} 内——本类只剩"取行 + 转交"，
 * 不再 import 任何模型族实现（原装配细节与"没有批级 60s 超时"的备案随之迁去那边）。</p>
 *
 * <p>失败语义不变：任一步失败抛异常——调用方只记警告、不设置描述器
 * （引擎随后对无描述能力走 "cannot view" 提示词）。</p>
 */
@Component
public class VlmDescriberWiring {

    private final ModelRuntime runtimeFactory;

    public VlmDescriberWiring(ModelRuntime runtimeFactory) {
        this.runtimeFactory = runtimeFactory;
    }

    /** 组装描述器；取模型/装配失败抛异常（调用方记警告后走无描述回落）。 */
    public ImageDescriberFunc create(String vlmModelId) {
        ModelRowView model = runtimeFactory.getVlmModel(vlmModelId);
        VlmPredictor predictor = runtimeFactory.vlmPredictorFor(model);
        return (imgBytes, prompt) -> predictor.predict(imgBytes, prompt);
    }
}
