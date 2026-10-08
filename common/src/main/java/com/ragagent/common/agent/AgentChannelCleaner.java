package com.ragagent.common.agent;

/**
 * agent 删除后清理其外部渠道绑定的扩展点。
 *
 * <p><b>为什么是端口</b>：包图拓扑序为
 * {@code im < session < agent < chatpipeline < memory < webfetch < datasource < auth}，
 * 而 {@code CustomAgentService.deleteAgent} 删完要清 IM 渠道 ⇒ 直接调
 * {@code im.service.ImService} 就成了回边。这条边是 {@code agent ⇄ im} 两两环与
 * 8 域间接环的源头之一（B111 实测：整图最小反馈边集只有 3 处，这是其中 1 处）。</p>
 *
 * <p><b>消费姿势</b>：{@code agent} 侧用 {@code ObjectProvider} 延迟解析——保留原有的
 * "破 Spring 级构造环 + 容器缺实现时静默跳过"语义（{@code ImService} 的字段反向依赖
 * {@code CustomAgentService}）。</p>
 */
public interface AgentChannelCleaner {

    /** 软删该 agent 名下的全部 IM 渠道（逐条广播配置变更）。 */
    void deleteChannelsByAgent(String agentId, long tenantId);
}
