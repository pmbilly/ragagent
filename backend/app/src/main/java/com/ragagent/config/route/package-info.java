/**
 * 统一路由策略面：角色下限矩阵（RbacRoutePolicies）与 API-Key 策略表
 * （APIKeyRoutePolicies）集中到本包，经 {@code RoutePolicyWiring} 灌进
 * {@code RoutePolicyInterceptor}——原 framework/web 的 RbacInterceptor 与
 * auth/apikey 的 APIKeyGateInterceptor 合一后的唯一拦截器。
 *
 * <p>判定顺序：ASYNC 跳过 → PathTenantMatch → API-Key 能力判定（仅原 Gate
 * 覆盖面内）→ web 角色下限。注册接口同时保留 addRule 与 registerGin，
 * 两张策略表的注册调用逐字不变。</p>
 */
package com.ragagent.config.route;
