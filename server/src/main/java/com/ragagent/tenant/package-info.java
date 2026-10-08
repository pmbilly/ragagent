/**
 * 租户域（B104 由 {@code common.tenant} 搬出）：租户实体 {@link com.ragagent.tenant.Tenant}、
 * {@code TenantMapper}、租户级配置 VO（{@code APIPrincipalConfig} / {@code ChatHistoryConfig} /
 * {@code ParserEngineConfig} / {@code RetrievalConfig} / {@code StorageEngineConfig}）、
 * {@code TenantConfigRedaction}（响应/更新面的密钥打码与合并）与 {@link com.ragagent.tenant.TenantLookup}。
 *
 * <p><b>为什么从 common 搬出来</b>：这些是<b>实体 + MyBatis mapper + 业务配置</b>——放在 common 意味着
 * "共享内核里住着数据模型与持久层"，是全仓最强的"common 不纯"信号（B101 的 R6 正是为此而设）。</p>
 *
 * <p><b>与 {@code common.tenant} 的分工</b>：留在 common 的是<b>跨域词汇</b>——{@code TenantRole}（13 个包
 * 的角色门禁）、{@code TenantProperties}（RBAC 配置）、{@code TenantConfigLookup}（L1 安全端口：
 * 只给 JsonNode 配置与存储视图）、{@code WebSearchConfig}（<b>L2</b> chatpipeline 也读 ⇒ 必须在 L1）。
 * 本域返回<b>实体</b>的端口 {@code TenantLookup} 只允许 L3 域（storage/knowledge）使用——
 * 若把它挪进 common，实体会被迫跟着留在 common；若把实体挪进本域而端口留 common，
 * common 就会反向依赖本域（R5 会红）。</p>
 *
 * <p>后续可选：把 {@code TenantLookup} 的返回收窄成视图（对齐 C2 的 {@code ChunkView} 做法），届时可并回
 * {@code common.tenant}。出向依赖：仅 {@code common}（{@code PgJsonTypeHandler}/{@code WebSearchConfig} 等），
 * 因此不引入任何环。</p>
 */
package com.ragagent.tenant;
