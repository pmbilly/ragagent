package com.ragagent.contract.tool;


/**
 * Agent 工具接口。
 *
 * <p>四方法语义：Name 唯一标识；Description 给模型看；Parameters 是
 * 参数 JSON Schema（<b>字节即契约</b>——发给 LLM 的工具块按此逐字节稳定）；
 * Execute 跑工具。</p>
 *
 * <p>error 通道约定：工具出错时不抛异常，而是返回 {@code success=false} 且
 * {@code error} 置好文案的 {@link ToolResult}（registry 依赖这个约定做截断与日志分流）。</p>
 */
public interface AgentTool {

    /** 工具唯一标识。 */
    String getName();

    /** 给模型看的描述。 */
    String getDescription();

    /**
     * 参数 JSON Schema（**纯 JDK 值**：对象→{@code Map}、数组→{@code List}；
     * 需按 JSON 树读时用 {@code JsonMappers.toJsonNode(...)} 归一）。
     */
    Object getParameters();

    /** 执行工具。 */
    ToolResult execute(ToolRequest request);
}
