/**
 * token 预算与上下文占用：{@code TokenEstimator}（token 估算，压缩与裁剪共用）、
 * {@code AgentBudgets}（轮次 / completion / 上下文预留等预算常量与换算）、
 * {@code ContextDiagnostics}（上下文占用分解，供日志与上下文漂移诊断）。
 *
 * <p>边界：只做"算数与常量"，不持有引擎状态、不发事件。谁触发预算决策（ObservePhase /
 * ReActIteration / compaction）留在根包与 {@code compaction/}。</p>
 */
package com.ragagent.agent.budget;
