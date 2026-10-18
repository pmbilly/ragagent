/**
 * 认证与租户控制面：登录/OIDC/令牌、用户与租户目录、RBAC 与会话身份。
 * 子域 {@code apikey/} 管理租户级 API Key 的签发、校验与路由策略。
 *
 * <p><b>种类</b>：控制面<br>
 * <b>入口</b>：{@code AuthController}、{@code TenantCatalogController}、{@code TenantMemberController}<br>
 * <b>对外契约面</b>：{@code UserLookupPort}（身份经 {@code TenantContext} 与 contract 载荷下传）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}、同模块 {@code tenant}。</p>
 */
package com.ragagent.auth;
