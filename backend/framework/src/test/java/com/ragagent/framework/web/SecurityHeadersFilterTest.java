package com.ragagent.framework.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@link SecurityHeadersFilter}：三头注入、已有头不覆盖、开关关闭、OPTIONS 同样生效。 */
class SecurityHeadersFilterTest {

    private MockHttpServletResponse run(boolean enabled, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter(enabled).doFilter(request, response, (req, rsp) -> { });
        return response;
    }

    @Test
    @DisplayName("普通请求注入三件安全响应头，取值逐字钉死")
    void injectsAllThreeHeadersWhenAbsent() throws Exception {
        MockHttpServletResponse response = run(true, new MockHttpServletRequest("GET", "/health"));
        assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(response.getHeader(SecurityHeadersFilter.FRAME_OPTIONS)).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader(SecurityHeadersFilter.REFERRER_POLICY)).isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("响应已有同名头时不覆盖：文件下载等路由自设的头保持原值")
    void doesNotOverridePreexistingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/files/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS, "custom-value");
        new SecurityHeadersFilter(true).doFilter(request, response, (req, rsp) -> { });
        assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS)).isEqualTo("custom-value");
        assertThat(response.getHeader(SecurityHeadersFilter.FRAME_OPTIONS)).isEqualTo("SAMEORIGIN");
    }

    @Test
    @DisplayName("开关关闭时不注入任何头（反代已统一注入的场景）")
    void disabledMeansNoHeaders() throws Exception {
        MockHttpServletResponse response = run(false, new MockHttpServletRequest("GET", "/health"));
        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test
    @DisplayName("OPTIONS 请求到过滤器时同样带头；注：启动态下预检在 CorsFilter 短路，不经过本过滤器")
    void optionsRequestAlsoGetsHeaders() throws Exception {
        MockHttpServletResponse response = run(true, new MockHttpServletRequest("OPTIONS", "/api/v1/models"));
        assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
    }
}
