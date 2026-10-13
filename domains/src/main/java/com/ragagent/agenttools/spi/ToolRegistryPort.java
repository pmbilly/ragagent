package com.ragagent.agenttools.spi;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.agenttools.AgentTool;
import com.ragagent.agenttools.ToolExecContext;
import com.ragagent.agenttools.ToolRegistry;
import com.ragagent.agenttools.ToolCancellation;
import com.ragagent.common.llm.ToolResult;

/**
 * 工具注册表端口（引擎侧接口）：MCP 适配层注册/摘除工具、共享执行管线的唯一通道。
 *
 * <p>语义与 {@link ToolRegistry} 的注册原语逐条对齐（first-wins、deferred 标记随注册设定），
 * 以免搬运过程中改变行为。实现由注册表自己提供（{@code ToolRegistry.port()}，包内匿名实现），
 * 因此注册表的包内封装不被打开——外部只能看到这些动作。</p>
 */
public interface ToolRegistryPort {

    /** 按名取工具；无则 null（等价于原来 catch {@code ToolNotFoundException} 后返回 null）。 */
    AgentTool get(String name);

    /** 当前工具快照（顺序不保证）。 */
    List<AgentTool> tools();

    /** 注册工具（first-wins：同名已注册时保留先到者）。 */
    void registerTool(AgentTool tool);

    /** 注册"延迟暴露"工具。 */
    void registerDeferredTool(AgentTool tool);

    /** 摘除工具（含其 deferred 标记）。 */
    void remove(String name);

    /** 只改 deferred 标记（不动工具本身）。 */
    void setDeferred(String name, boolean deferred);

    /** 目录解析后的共享执行管线入口。 */
    ToolResult executeInternal(ToolCancellation cancellation, ToolExecContext meta, AgentTool tool, JsonNode args);
}
