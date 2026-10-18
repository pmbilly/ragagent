/**
 * agent 侧提示词构件：{@code AgentPromptTemplates}（系统提示词模板按 mode 选取，经
 * {@code platform.prompt.PromptSource} 装载）、{@code GroundingPrompt}（按当前工具清单生成
 * 内容接地指引）。
 *
 * <p><b>值类型</b>：{@code RecentDocInfo} / {@code SelectedDocumentInfo} /
 * {@code PinnedMCPServiceInfo} / {@code PinnedSkillInfo} / {@code PinnedSkillInstructions} /
 * {@code KnowledgeBaseInfo} / {@code PlaceholderDefinition} / {@code SystemPromptSection}
 * 单独成文件；{@code BuildSystemPromptOptions} 仍留根包（引用根包 {@code SkillMetadata}）。</p>
 *
 * <p>边界：本包只放 agent 专属的提示词构件与值类型；文案本体与装载在
 * {@code platform.prompt}（{@code platform/src/main/resources/prompts/*.yaml}），
 * 段落选择与拼接（{@code AgentPrompts} 段表 / {@code ConversationAssembler}）暂留根包
 * （与 {@code AgentEngine} 共享包内 seam）。</p>
 */
package com.ragagent.agent.prompt;
