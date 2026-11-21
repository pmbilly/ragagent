package com.ragagent.agenttools.api;

/** 数据分析插件的工具会话 seam：按会话 ID 建一次装载 / 执行 / 清理会话。 */
public interface DataAnalysisSessionFactory {

    DataAnalysisSession create(String sessionId);
}
