package com.ragagent.contract.model.chat;

/**
 * 函数调用详情。
 * JSON 字段序 = 声明序；键名即字段名，不需要 mixin 承载。
 */
public class FunctionCall {

    private String name = "";
    /** JSON 字符串（**不是**对象；Ollama 路径另用 map）。 */
    private String arguments = "";

    public FunctionCall() {
    }

    public FunctionCall(String name, String arguments) {
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    public String getName() { return name; }
    public void setName(String v) { name = v == null ? "" : v; }
    public String getArguments() { return arguments; }
    public void setArguments(String v) { arguments = v == null ? "" : v; }
}
