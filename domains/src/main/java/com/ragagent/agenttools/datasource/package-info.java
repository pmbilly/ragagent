/**
 * agent 数据分析工具族：{@code data_analysis} / {@code data_schema} 两工具 + DuckDB 生产实现
 * （{@code AnalysisDuckDbJdbc}）与 chatpipeline 会话桥（{@code DataAnalysisSessionBridge}）。
 *
 * <p><b>改名</b>：本面原为 {@code agenttools.data}（{@code data/}），改名为
 * {@code agenttools.datasource}（{@code datasource/}）以对齐方案 §8.8.3 的目标树命名——
 * 类集与目标树**完全一致**，纯改名（无搬迁、无新增边）。</p>
 */
package com.ragagent.agenttools.datasource;
