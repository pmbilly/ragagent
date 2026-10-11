/**
 * agent 侧提示词构件：{@code AgentPromptTemplates}（系统提示词模板按 mode 选取，经
 * {@code common.prompt.PromptSource} 装载）、{@code GroundingPrompt}（按当前工具清单生成
 * 内容接地指引）。
 *
 * <p>边界：本包只放"agent 专属的提示词构件"；**文案本体与装载**在 L1
 * （{@code common.prompt.PromptSource} / {@code common/resources/prompts/*.yaml}），
 * 段落选择与拼接（{@code AgentPrompts} / {@code PromptAssembly}）暂留根包，待 F5
 * 组装收口时一并搬入并与 {@code PromptComposer} 合流。</p>
 */
package com.ragagent.agent.prompt;
