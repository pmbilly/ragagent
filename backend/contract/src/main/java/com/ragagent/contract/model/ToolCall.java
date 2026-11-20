package com.ragagent.contract.model;

import java.util.Map;

/**
 * 工具调用。
 *
 * <p>JSON 字段序 = 声明序。provider_metadata 为空时省略——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class ToolCall {

    private String id = "";
    /** "function" */
    private String type = "function";
    private FunctionCall function = new FunctionCall();
    /**
     * 厂商特有状态（如 Gemini 的 extra_content），必须随 assistant 工具调用原样往返，
     * 以免把厂商字段教给核心 agent 代码。序列化时 map 按键字母序输出，
     * 保持与既有线格式一致。
     *
     * <p>值持**不透明 JSON**（契约面以 {@code Object} 承载，运行时是 {@code JsonNode} 或纯 JDK 值）；
     * 两侧互转经 {@code llm.domain.ProviderMetadataMaps}（树形 ⇄ 不透明）或
     * {@code JsonMappers.toJsonNode} 单值一转。</p>
     */
    private Map<String, Object> providerMetadata;

    /**
     * 以下为**请求内**观测状态（不参与线上 JSON，mixin 里 {@code @JsonIgnore}）。
     * ModelArguments 保留模型发出的原始 JSON，而 Function.Arguments 在工具执行前
     * 会被解码成持久的应用标识。这些字段**绝不可**回传 provider 或持久化进聊天历史。
     */
    private String modelArguments;
    private String argumentResolution;
    private java.util.List<String> unresolvedHandles;

    public String getId() { return id; }
    public void setId(String v) { id = v == null ? "" : v; }
    public String getType() { return type; }
    public void setType(String v) { type = v == null ? "function" : v; }
    public FunctionCall getFunction() { return function; }
    public void setFunction(FunctionCall v) { function = v == null ? new FunctionCall() : v; }
    public Map<String, Object> getProviderMetadata() { return providerMetadata; }
    public void setProviderMetadata(Map<String, Object> v) { providerMetadata = v; }
    public String getModelArguments() { return modelArguments; }
    public void setModelArguments(String v) { modelArguments = v; }
    public String getArgumentResolution() { return argumentResolution; }
    public void setArgumentResolution(String v) { argumentResolution = v; }
    public java.util.List<String> getUnresolvedHandles() { return unresolvedHandles; }
    public void setUnresolvedHandles(java.util.List<String> v) { unresolvedHandles = v; }

    /** 工具名；派生方法，不上线（mixin 里 {@code @JsonIgnore}）。 */
    public String getToolName() {
        return function == null ? "" : function.getName();
    }
}
