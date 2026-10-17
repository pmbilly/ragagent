package com.ragagent.contract.model;

import java.util.List;

/**
 * Ollama 管理能力端口（业务域 → 模型运行时）。
 *
 * <p> 契约化：初始化域的 ollama 管理端点（status/models/check/download）原先直注
 * {@code model-runtime} 的 {@code OllamaService} 实现类；本端口只收域侧真调用的六个方法。
 * 实现侧（{@code OllamaService}）保留原有的探活/单例语义——"已可用则跳过 StartService"
 * 依赖 bean 级单例，故端口实现方必须是单例 bean。</p>
 */
public interface OllamaAdminPort {

    /** 探活并（必要时）启动/连接；失败按实现侧的 optional 语义处理。 */
    void startService();

    /** 当前是否可用（跨请求共享的实例状态）。 */
    boolean isAvailable();

    /** 单个模型是否已就位。 */
    boolean isModelAvailable(String modelName);

    /** 服务版本（不可用时抛运行时异常）。 */
    String getVersion();

    /** 本地模型清单（name/size/digest/modified_at）。 */
    List<OllamaModelInfo> listModelsDetailed();

    /** 拉取模型并逐次回报进度（与实现侧内部的原始 JSON 回调重载区分，故用独立方法名）。 */
    void pullModel(String modelName, OllamaProgressListener listener);
}
