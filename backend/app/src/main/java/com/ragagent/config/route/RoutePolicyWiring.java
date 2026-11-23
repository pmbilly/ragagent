package com.ragagent.config.route;

import com.ragagent.framework.config.TenantProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一路由策略的装配点：一张角色下限矩阵（{@link RbacRoutePolicies}，375 条）
 * 加一张 API-Key 策略表（{@link APIKeyRoutePolicies}，291 条）灌进同一个
 * {@link RoutePolicyInterceptor}。登记顺序即语义，两表注册调用逐字保留。
 *
 * <p>原 {@code WebConfig.addInterceptors} 里 {@code new RbacInterceptor} 与
 * {@code new APIKeyGateInterceptor} 两段装配收拢到本类，组合根只负责
 * {@code registry.addInterceptor(routePolicyInterceptor)}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RoutePolicyWiring {

    @Bean
    public RoutePolicyInterceptor routePolicyInterceptor(TenantProperties tenantProperties) {
        RoutePolicyInterceptor interceptor = new RoutePolicyInterceptor(tenantProperties);
        RbacRoutePolicies.registerAll(interceptor);
        APIKeyRoutePolicies.registerAll(interceptor);
        return interceptor;
    }
}
