package com.ragagent.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ragagent.common.mybatis.FullTableWriteGuard;
import com.ragagent.common.mybatis.TenantFilterGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：物理分页 + total 统计 + 全表改删防护 + 租户过滤探测。
 * ListKnowledge 真分页依赖分页拦截器；{@link FullTableWriteGuard} 机器强制
 * 「禁止无条件全局更新/删除」；{@link TenantFilterGuard} 探测 SELECT 缺失租户谓词
 * （B71 定性后默认 enforce，env 可降 alert/off）。顺序按 MP 官方建议：改造 SQL 的在前
 * （分页），不改写 SQL 的放最后（三道防护）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Value("${weknora.persistence.tenant-filter-guard:enforce}") TenantFilterGuard.Mode guardMode) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(1000L);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new FullTableWriteGuard());
        interceptor.addInnerInterceptor(new TenantFilterGuard(guardMode));
        return interceptor;
    }
}
