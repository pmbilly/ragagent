/**
 * 租户**跨域词汇**（拆分后留下来的四类"谁都要用"的东西）：
 *
 * <ul>
 *   <li>{@link com.ragagent.contract.tenant.TenantRole} —— 租户角色与能力等级（rank 决定最小角色校验），
 *       <b>18 个包</b>在用（auth/knowledge/mcp/embed/wiki/…，以及 {@code common.web.RbacInterceptor}）；</li>
 *   <li>{@link com.ragagent.platform.config.TenantProperties} —— {@code weknora.tenant} 配置项
 *       （RBAC 开关、自助建租户等），auth/config/{@code common.web} 共用；</li>
 *   <li>{@link com.ragagent.common.tenant.TenantConfigLookup} —— 配置读取端口，只暴露
 *       {@code JsonNode} 配置与 {@link com.ragagent.common.tenant.TenantConfigLookup.TenantStorageView}
 *       （L1 安全），供 L2（retrieval）与各 L3 域共用；</li>
 *   <li>{@link com.ragagent.contract.tenant.WebSearchConfig} —— 租户级联网检索配置，
 *       <b>L2 chatpipeline</b> 也要读 ⇒ 必须留在 L1（否则新增 L2→L3 边）。</li>
 * </ul>
 *
 * <p><b>租户域的实现在 {@code com.ragagent.tenant}</b>（从 common 搬出）：
 * {@code Tenant} 实体 + {@code TenantMapper} +
 * 租户级配置 VO（APIPrincipalConfig/ChatHistoryConfig/ParserEngineConfig/RetrievalConfig/StorageEngineConfig）
 * + {@code TenantConfigRedaction} + {@code TenantLookup}（返回实体的端口，仅限 L3 使用）。
 * 搬出理由：common 不该住实体与 mapper（棘轮 R6 盯的就是这类痕迹）。</p>
 */
package com.ragagent.common.tenant;
