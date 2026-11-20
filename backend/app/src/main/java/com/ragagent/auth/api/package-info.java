/**
 * 认证 · 域内视图：{@link com.ragagent.auth.api.AuthUserErrors}（认证错误码与文案）。
 *
 * <p>跨域消费方应注入 {@code contract.auth} 的端口（{@code AdminUserPort} /
 * {@code AdminUserCommandPort} / {@code UserLookupPort}）；端口实现位于 {@code auth.adapter}。
 * {@code CreateUserResult} 已上提到 {@code contract.auth}。</p>
 *
 * <p>依赖 {@code contract}、{@code platform}、{@code auth}；入口 {@link com.ragagent.auth.api.AuthUserErrors}。</p>
 */
package com.ragagent.auth.api;
