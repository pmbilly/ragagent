/**
 * 租户域：租户实体、MyBatis 持久化、租户级配置（API principal / 聊天历史 / 解析引擎 /
 * 检索 / 存储引擎）与配置脱敏。租户管理用例在 {@code auth}，跨域只读经
 * {@code contract.tenant} 的 {@code TenantConfigLookup}。
 *
 * <p><b>种类</b>：控制面<br>
 * <b>入口</b>：无 HTTP 面；由 {@code auth} 的租户管理面调用<br>
 * <b>对外契约面</b>：{@code TenantStorageQuotaPort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 *
 * <p>租户配置以 jsonb 落库；返回实体的 {@code TenantLookup} 只允许同模块域使用。</p>
 */
package com.ragagent.tenant;
