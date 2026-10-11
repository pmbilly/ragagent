/**
 * 工具运行时横切件（不放具体工具）：{@code SearchAuth}（检索范围鉴权）、
 * {@code ToolResultPersist}（工具结果的客户端 / 存储投影）、{@code MessageSanitizer} 与
 * {@code ThinkBlocks} / {@code ThinkStreamSplitter}（消息与思考块清洗 / 切分）、
 * {@code NormalizeToolCallId}（工具调用 id 归一）、{@code JsonRepair}（模型 JSON 修复）、
 * {@code PythonSyntax}（数据分析工具的 Python 语法辅助）。
 *
 * <p>边界：这些件被 agent 根包（各 Phase）、{@code tools/*} 能力子包与 session 侧共同消费，
 * 全部 {@code public}、与 {@code ToolRegistry}/MCP 族**无**包私有耦合——搬进本包只为把
 * "工具框架 / 横切件 / 具体工具"三件事分开。具体工具按能力留在
 * {@code tools/knowledge|wiki|sql|data|web}；MCP 族暂留 {@code tools/} 根
 * （与 {@code ToolRegistry} 有包私有互访，需先解耦再搬）。</p>
 */
package com.ragagent.agent.tools.runtime;
