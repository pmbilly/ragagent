package com.ragagent.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ragagent.common.mybatis.FullTableWriteGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：物理分页 + total 统计 + 全表改删防护。
 * ListKnowledge 真分页依赖分页拦截器；{@link FullTableWriteGuard} 机器强制
 * 「禁止无条件全局更新/删除」。顺序按 MP 官方建议：改造 SQL 的在前（分页），
 * 不改写 SQL 的放最后（防护）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(1000L);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new FullTableWriteGuard());
        return interceptor;
    }
}
