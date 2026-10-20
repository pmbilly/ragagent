package com.ragagent.contract.agent;

import java.util.List;


/**
 * agent 目录读面（L1）：跨域消费方读 agent 元信息 / 配置 / 推荐问题，不 import agentmanagement 实现。
 *
 * <p><b>为什么是端口</b>：embedchannel 与 im 的渠道流程此前直连 agentmanagement 的
 * {@code CustomAgentMapper}（持久层 ✗）、{@code CustomAgentService}（域服务 ✗）与
 * {@code BuiltinAgentRegistry}，并把 {@code CustomAgentEntity} 当跨域视图传递（实体出域 ✗）。
 * 本端口把读路径收回属主域实现（{@code agentmanagement/api/AgentLookupAdapter}），
 * 调用方只 import 本端口与 {@code AgentView}。</p>
 *
 * <p><b>归属说明</b>：方案 的 {@code contract.agent/AgentCatalog} 目标形态是纯 JDK（typed records）；
 * 本面已去 Jackson 并**入 {@code :contract}**——{@code configJson} 串保持字符串、
 * 配置/推荐问题改为**纯 JDK 值**（对象→{@code Map}、数组→{@code List}），读面用
 * {@code contract.json.JsonValues}（零依赖；语义与 {@code JsonNode} 读面逐条对齐）。</p>
 */
public interface AgentLookup {

    /**
     * agent 存在且属于该租户（含内建注册表兜底）。
     *
     * <p>未知 agent、跨租户、空 id、查询异常一律 {@code false}——**不抛**：错误语义（如 embed 的
     * 500 "operation failed"）由调用方决定。</p>
     */
    boolean isAgentOwned(long tenantId, String agentId);

    /**
     * 该租户下存在 agent 数据行（**不含**内建注册表兜底）。
     *
     * <p>与 {@code #isAgentOwned} 的差别：内建 agent 未落行时本方法为 {@code false}——
     * im 换绑校验按"行存在"判存（内建 id 未落行不可绑定）。未知 agent、跨租户、空 id、
     * 查询异常、行租户列缺失或不匹配一律 {@code false}。</p>
     */
    boolean hasAgentRow(long tenantId, String agentId);

    /**
     * agent 视图（{@code configJson} ＝ 行上的原始配置串，未归一化）；不存在 / 查询失败返回 {@code null}。
     *
     * <p>内建 agent 未落行时返回注册表合成视图（与行视图同形；configJson 为内建配置串）。</p>
     */
    default AgentView view(String agentId) {
        return view(agentId, null);
    }

    /**
     * 带 locale 的 agent 视图：内建 agent 的 name/description/avatar 按 locale 本地化
     * （{@code null} ＝ 注册表默认语言；im 命令面按 "zh-CN" 读取）。configJson 不受 locale 影响。
     */
    AgentView view(String agentId, String locale);

    /** 内建 agent 的固定序 id 全集（渠道列表查询用于"agent 行存活或内建"判定）。 */
    List<String> builtinAgentIds();

    /**
     * 归一化 agent config 串：JSON → 树 + 补缺省（就地）；非法串 / 非对象 ⇒ {@code null}。
     *
     * <p>调用方的"原始串"（如 {@code AgentView#configJson()}）经本方法得到运行面配置树；
     * 归一化规则是 agentmanagement 的领域知识，不随调用方复制。</p>
     */
    Object defaultedConfig(String configJson);

    /** agent 的推荐问题（按 KB 过滤）；无配置 / 失败返回 {@code null}（调用方按"无推荐"处理）。 */
    List<Object> suggestedQuestions(String agentId, List<String> kbIds, int limit);

    /**
     * agent 只读视图（跨域传递用；不再传实体）。
     *
     * <p>{@code tenantId} 是解析该 agent 时的租户归属：会话域用它决定检索租户、
     * 并判断 agent 作用域是否适用于当前会话，故必须随视图一起带出。</p>
     */
    record AgentView(String id, String name, String description, String avatar,
                     String configJson, Long tenantId) {
    }
}
