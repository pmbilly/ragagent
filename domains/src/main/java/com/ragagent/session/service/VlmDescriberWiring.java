package com.ragagent.session.service;

import com.ragagent.agent.AgentEngine;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.limiter.Release;
import com.ragagent.chatpipeline.spi.ModelRuntimePort;
import com.ragagent.retrieval.vlm.VlmClient;
import com.ragagent.retrieval.vlm.VlmHttpTransport;
import com.ragagent.platform.tracing.decorators.LangfuseVlm;
import org.springframework.stereotype.Component;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;

/**
 * 工具结果图片 VLM 描述器的装配：取 VLM 模型并组装 predict 回调。
 * 调用面是 {@code VlmClient.predict(config, transport, images, prompt)}
 * （逐张图片调用，载荷形如 {@code [][]byte{imgBytes}}）。
 *
 * <p>装饰顺序：并发闸门在最外层（先取槽，再 langfuse
 * 计时，最后真实 provider 往返）——wrapVLMConcurrency 的注释明确"等待不计入
 * debug/langfuse 计时"。</p>
 *
 * <p><b>已知差异（备案）</b>：没有批级 60s 超时——Java 侧调用是阻塞的、没有协作取消通道，
 * 实际超时由 {@code VLM_HTTP_TIMEOUT_SECONDS}（缺省 180s）
 * 在 HTTP 层兜底。</p>
 */
@Component
public class VlmDescriberWiring {

    private final ModelRuntimePort runtimeFactory;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final VlmClient.Transport transport;

    /**
     * Spring 装配面（生产）：出站通道缺省为 {@link VlmHttpTransport}。
     * 显式 {@code @Autowired}——类里还有一个测试用的包内构造，不标注时 Spring 挑错构造。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public VlmDescriberWiring(ModelRuntimePort runtimeFactory,
            ConcurrencyGovernor concurrencyGovernor) {
        this(runtimeFactory, concurrencyGovernor, new VlmHttpTransport());
    }

    /** 测试接缝：出站通道注入（生产缺省 {@link VlmHttpTransport}）。 */
    VlmDescriberWiring(ModelRuntimePort runtimeFactory, ConcurrencyGovernor concurrencyGovernor,
            VlmClient.Transport transport) {
        this.runtimeFactory = runtimeFactory;
        this.concurrencyGovernor = concurrencyGovernor;
        this.transport = transport;
    }

    /**
     * 组装描述器；任一步失败抛异常——调用方只记警告、不设置
     * 描述器（引擎随后对无描述能力走 "cannot view" 提示词）。
     */
    public AgentEngine.ImageDescriberFunc create(String vlmModelId) {
        ModelRowView model = runtimeFactory.getVlmModel(vlmModelId);
        VlmClient.VlmConfig config = runtimeFactory.vlmConfigFor(model);
        int limit = model.parameters() == null ? 0 : model.parameters().getMaxConcurrency();
        String modelName = model.name() == null ? "" : model.name();
        String modelId = model.id() == null ? "" : model.id();

        LangfuseVlm.PredictFn predict = LangfuseVlm.wrap(
                (images, prompt) -> VlmClient.predict(config, transport, images, prompt),
                modelName, modelId);

        return (imgBytes, prompt) -> {
            try (Release release = concurrencyGovernor.gateNamedN(modelId, modelName, limit)) {
                try {
                    return predict.predict(new byte[][] {imgBytes}, prompt);
                } catch (Exception e) {
                    throw new RuntimeException(
                            e.getMessage() == null ? e.toString() : e.getMessage(), e);
                }
            }
        };
    }
}
