package com.ragagent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.tool.ToolDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A4：并发白名单只许放**无会话状态**的读工具。
 *
 * <p>`knowledge_search` / `grep_chunks` 的输出带 `already_seen` 标记，依据是工具实例上的
 * 会话级 `seenChunks`（`KnowledgeSearchTool.seenChunks` / `GrepChunksTool.seenChunks`）。
 * 一旦并发，多线程同时写这个非线程安全集合：① 数据竞争；② 谁算"已见"取决于**完成顺序**
 * 而非**调用顺序** ⇒ 同一轮乱序完成得到不同输出（验收：乱序完成稳定回填）。</p>
 *
 * <p>恢复并发需要把"取数"与"渲染+标记"分成两段（渲染由单 writer 按调用序执行）——
 * 见 HANDOFF 登记；在那之前这两个工具是**执行屏障**。</p>
 */
class ExecutionPolicyStatefulReadTest {

    @Test
    @DisplayName("会话级有状态读工具是屏障，不得并发")
    void statefulReadToolsAreBarriers() {
        assertThat(ExecutionPolicy.canRunConcurrently(ToolDefinitions.TOOL_KNOWLEDGE_SEARCH))
                .as("knowledge_search 的已见去重要按调用序写 seenChunks")
                .isFalse();
        assertThat(ExecutionPolicy.canRunConcurrently(ToolDefinitions.TOOL_GREP_CHUNKS))
                .as("grep_chunks 同上")
                .isFalse();
    }

    @Test
    @DisplayName("无状态读工具仍在并发白名单")
    void statelessReadToolsStayConcurrent() {
        assertThat(ExecutionPolicy.canRunConcurrently(ToolDefinitions.TOOL_WEB_SEARCH)).isTrue();
        assertThat(ExecutionPolicy.canRunConcurrently(ToolDefinitions.TOOL_READ_FILE)).isTrue();
        assertThat(ExecutionPolicy.canRunConcurrently(ToolDefinitions.TOOL_LIST_KNOWLEDGE_CHUNKS))
                .isTrue();
    }
}
