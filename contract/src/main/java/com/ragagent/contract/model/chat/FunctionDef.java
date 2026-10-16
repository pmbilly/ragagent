package com.ragagent.contract.model.chat;

/**
 * 函数定义。JSON 字段序 = 声明序，三者均恒输出——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。
 */
public class FunctionDef {

    private String name = "";
    private String description = "";
    /**
     * JSON Schema（自由度高的工具入参形状）。
     *
     * <p>持**不透明 JSON**（契约面以 {@code Object} 承载，运行时是 {@code JsonNode} 或纯 JDK 值）；
     * 需要树形操作的一侧经 {@code JsonMappers.toJsonNode} 一转即回。</p>
     */
    private Object parameters;

    public FunctionDef() {
    }

    public FunctionDef(String name, String description, Object parameters) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public void setName(String v) { name = v == null ? "" : v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v == null ? "" : v; }
    public Object getParameters() { return parameters; }
    public void setParameters(Object v) { parameters = v; }
}
