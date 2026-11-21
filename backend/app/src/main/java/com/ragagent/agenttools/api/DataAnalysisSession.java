package com.ragagent.agenttools.api;

import com.ragagent.framework.vocabulary.ToolResult;

/** 一次数据装载 / 执行 / 清理会话。 */
public interface DataAnalysisSession {

    TableSchema loadFromKnowledge(KnowledgeData knowledge);

    /** 执行查询；参数是不透明 JSON，由实现侧按其 JSON 库解释。 */
    ToolResult execute(Object args);

    void cleanup();
}
