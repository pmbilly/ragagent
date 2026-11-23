package com.ragagent.framework.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 安全响应头的启动态接线：默认开启时，正常响应（/health）与鉴权拒绝响应（401）
 * 都要带三件头。头的取值语义与「不覆盖」行为由 framework 的
 * {@code SecurityHeadersFilterTest} 在过滤器级覆盖，这里只钉装配（顺序、注册、生效）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthResponseCarriesSecurityHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/health")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader(SecurityHeadersFilter.FRAME_OPTIONS)).isEqualTo("SAMEORIGIN");
        assertThat(result.getResponse().getHeader(SecurityHeadersFilter.REFERRER_POLICY)).isEqualTo("no-referrer");
    }

    @Test
    void unauthorizedResponseCarriesSecurityHeaders() throws Exception {
        // 鉴权短路写 401 的路径同样带头：过滤器在认证之前，头早已在响应上
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getResponse().getHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader(SecurityHeadersFilter.REFERRER_POLICY)).isEqualTo("no-referrer");
    }
}
