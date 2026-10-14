package com.ragagent.common.agent;

import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * agent 目录读面（L1）：跨域消费方读 agent 元信息 / 配置 / 推荐问题，不 import agentmanagement 实现。
 *
 * <p><b>为什么是端口</b>：embedchannel 的渠道流程此前直连 agentmanagement 的 {@code CustomAgentMapper}
 * （持久层 ✗）、{@code CustomAgentService}（域服务 ✗）与 {@code BuiltinAgentRegistry}，并把
 * {@code CustomAgentEntity} 当跨域视图传递（实体出域 ✗）。本端口把读路径收回属主域实现
 * （{@code agentmanagement/api/AgentLookupAdapter}），调用方只 import 本端口与 {@link AgentView}。</p>
 *
 * <p><b>归属说明</b>：方案 §8.1 的 {@code contract.agent/AgentCatalog} 目标形态是纯 JDK（typed records）；
 * 本面先按现状落 L1（含 {@code configJson} 字符串与 {@link ArrayNode}，R1 不允许契约面出现 Jackson 类型），
 * 待「{@code JsonNode} 载体」K 级重设计时再上提契约面。</p>
 */
public interface AgentLookup {

    /**
     * agent 存在且属于该租户（含内建注册表兜底）。
     *
     * <p>未知 agent、跨租户、空 id、查询异常一律 {@code false}——**不抛**：错误语义（如 embed 的
     * 500 "operation failed"）由调用方决定。</p>
     */
    boolean isAgentOwned(long tenantId, String agentId);

    /** agent 视图（{@code configJson} ＝ 行上的原始配置串，未归一化）；不存在 / 查询失败返回 {@code null}。 */
    AgentView view(String agentId);

    /** agent 的推荐问题（按 KB 过滤）；无配置 / 失败返回 {@code null}（调用方按"无推荐"处理）。 */
    ArrayNode suggestedQuestions(String agentId, List<String> kbIds, int limit);

    /** agent 只读视图（跨域传递用；租户归属由 {@link #isAgentOwned} 表达，不再传实体）。 */
    record AgentView(String id, String name, String description, String avatar, String configJson) {
    }
}
