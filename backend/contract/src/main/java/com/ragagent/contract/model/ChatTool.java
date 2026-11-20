package com.ragagent.contract.model;

/**
 * 工具定义。JSON 字段序 = 声明序，两者均恒输出——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。
 */
public class ChatTool {

    /** 恒为 "function" */
    private String type = "function";
    private FunctionDef function = new FunctionDef();

    public ChatTool() {
    }

    /**
     * @param parameters JSON Schema，持**不透明 JSON**（运行时是 {@code JsonNode} 或纯 JDK 值）
     */
    public ChatTool(String name, String description, Object parameters) {
        this.function = new FunctionDef(name, description, parameters);
    }

    public String getType() { return type; }
    public void setType(String v) { type = v == null ? "function" : v; }
    public FunctionDef getFunction() { return function; }
    public void setFunction(FunctionDef v) { function = v == null ? new FunctionDef() : v; }
}
