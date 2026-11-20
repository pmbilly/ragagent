/**
 * ReAct 引擎域：AgentEngine 门面与段协作者（think/act/observe/prompt/finalize/steer/context-debug/ReAct 迭代）、
 * 提示词装配、token 预算与取消探测。对外入口 = {@code AgentEngine.execute}。
 *
 * <p>子包：{@code tool/}（工具框架：契约/定义/注册/校验/执行上下文/运行时修正）、
 * {@code compaction/}（上下文压缩）、{@code skills/}（技能装载）、{@code domain/}（引擎值类型）、
 * {@code budget/}（token 预算与上下文占用诊断）、{@code prompt/}（提示词模板与接地指引）。
 * 模型输出上下文协议在 {@code model-runtime} 的 {@code modelcontext}；智能体配置/技能目录管理在
 * {@code domains} 的 {@code agentmanagement}；业务工具实现在 {@code domains} 的 {@code agenttools}。</p>
 *
 * <h2>一轮的时序（loop）</h2>
 * <ol>
 * <li>裁剪 / 压缩上下文 —— {@code ObservePhase.manageContextWindow}（策略在 {@code compaction/}）</li>
 * <li>注入用户中途插话 —— {@code SteerIntake.drainSteerMessages}</li>
 * <li>调模型 —— {@code ThinkPhase.callLLMWithRetry}（消息与提示词由 {@code ConversationAssembler} 装配）</li>
 * <li>判定响应 —— {@code ObservePhase.analyzeResponse}（终答 / 继续 / 中断）</li>
 * <li>执行工具 —— {@code ActPhase.executeToolCalls}（工具定义在 {@code tool/}）</li>
 * <li>回灌结果与图片 —— {@code ObservePhase.appendToolResults} / {@code appendToolImages}</li>
 * </ol>
 * <p>循环驱动：{@code AgentEngine.executeLoop}（轮首刷新工具定义、取消探测、迭代预算判定）；
 * 单轮编排：{@code ReActIteration}；收尾兜底：{@code FinalizePhase}。类头「主循环地图」见 {@code AgentEngine}。</p>
 *
 * <h2>想改 X 去哪</h2>
 * <p>一轮怎么跑 → {@code AgentEngine.executeLoop} + {@code ReActIteration}；模型调用/重试/思考块 → {@code ThinkPhase}；
 * 工具执行与审批 → {@code ActPhase}（+ {@code tool/}）；上下文窗口与压缩触发 → {@code ObservePhase} + {@code compaction/}；
 * 系统提示词与消息装配 → {@code AgentPrompts} + {@code ConversationAssembler} + {@code prompt/}；
 * token 预算与占用诊断 → {@code budget/}；工具框架与注册 → {@code tool/}；终答与最大迭代兜底 → {@code FinalizePhase}；
 * 中途插话 → {@code SteerIntake} / {@code SteerSink}。</p>
 *
 * <p>八个段协作者与 {@code AgentEngine} 共享包内可见状态（seam），仍留根包；不为导航拆 {@code loop/}。</p>
 */
package com.ragagent.agent;
