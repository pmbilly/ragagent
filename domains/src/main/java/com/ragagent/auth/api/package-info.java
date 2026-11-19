/**
 * 认证 · 域内视图：{@link com.ragagent.auth.api.CreateUserResult} 与
 * {@link com.ragagent.auth.api.AuthUserErrors}（system 等域经此读认证结果）。
 *
 * <p>跨域消费方应注入 {@code contract.auth} 的端口；端口实现已移到 {@code auth.adapter}。</p>
 *
 * <p>依赖 {@code contract}、{@code platform}、{@code auth}；入口 {@link com.ragagent.auth.api.CreateUserResult}。</p>
 */
package com.ragagent.auth.api;
