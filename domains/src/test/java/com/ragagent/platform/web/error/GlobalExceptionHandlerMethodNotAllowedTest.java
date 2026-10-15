package com.ragagent.platform.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.ragagent.contract.error.ErrorCode;

/**
 * 方法不允许 → 405（而非 catch-all 的 500）。
 *
 * <p>回归背景：GET 打到仅 {@code @PostMapping} 的路由，Spring 抛
 * {@link HttpRequestMethodNotSupportedException}，此前落 catch-all ⇒ 回 500 且打 ERROR 日志 ✗。</p>
 */
class GlobalExceptionHandlerMethodNotAllowedTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException ⇒ 405 + 通用段码信封")
    void methodNotSupportedMapsTo405() {
        var resp = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET"), new MockHttpServletRequest());

        assertThat(resp.getStatusCode().value()).isEqualTo(405);
        // 历史形态（非 @ApiResult 路由）：{"error":{code,message,details}}
        @SuppressWarnings("unchecked")
        var body = (java.util.Map<String, Object>) resp.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        var error = (java.util.Map<String, Object>) body.get("error");
        assertThat(error.get("code")).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.value());
        assertThat(error.get("message")).isEqualTo("Method Not Allowed");
    }
}
