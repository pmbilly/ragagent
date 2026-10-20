package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.ragagent.platform.persistence.SoftDeleteFilterGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 装配面：拦截器链里的软删守卫档位必须与配置一致。
 *
 * <p>只在 {@code @SpringBootTest} 里验证——直接 new 守卫测不出
 * "配置项没接上"这类错误。</p>
 */
@SpringBootTest
class SoftDeleteGuardWiringTest {

    @Autowired
    private MybatisPlusInterceptor interceptor;

    @Value("${weknora.persistence.soft-delete-guard:alert}")
    private String expectedMode;

    @Test
    void modeMatchesProperty() {
        SoftDeleteFilterGuard guard = interceptor.getInterceptors().stream()
                .filter(SoftDeleteFilterGuard.class::isInstance)
                .map(SoftDeleteFilterGuard.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(guard.mode())
                .isEqualTo(SoftDeleteFilterGuard.Mode.valueOf(expectedMode.toUpperCase()));
    }
}
