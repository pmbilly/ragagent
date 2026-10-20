/**
 * 智能体管理面：内建智能体注册表与类型预设、自定义智能体 CRUD（{@code custom_agents}）、
 * 技能目录、推荐问题流与 agent config jsonb。本域只回答“有哪些智能体、各是什么配置”，
 * 运行与工具调度在 {@code agent-engine}。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：{@code AgentController}、{@code SkillCatalogAdminController}<br>
 * <b>对外契约面</b>：无（配置经 {@code contract.agent} 载荷消费）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 *
 * <p>agent config jsonb 的内层键（{@code agent_mode}/{@code system_prompt} 族）由管理面、
 * 引擎、嵌入渠道与会话共享，保持 snake。</p>
 */
package com.ragagent.agentmanagement;
