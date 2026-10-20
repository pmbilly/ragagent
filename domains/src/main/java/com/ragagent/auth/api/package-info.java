/**
 * 认证 · 域内访问面：{@code UserLookupAdapter}（实现 {@code contract.auth.UserLookupPort}）、
 * {@code AdminUserAdapter}，以及域内视图（{@link com.ragagent.auth.api.CreateUserResult}）。
 *
 * <p>跨域消费方应注入 {@code contract.auth} 的端口；本包类型只供认证族内使用。</p>
 *
 * <p>依赖 {@code contract}、{@code platform}、{@code auth}。</p>
 */
package com.ragagent.auth.api;
