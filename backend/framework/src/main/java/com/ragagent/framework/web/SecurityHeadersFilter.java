package com.ragagent.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 全局安全响应头：X-Content-Type-Options: nosniff、X-Frame-Options: SAMEORIGIN、
 * Referrer-Policy: no-referrer。
 *
 * <p>三个头均为「已存在则不覆盖」：文件下载等路由会自行设置 nosniff 与业务
 * Cache-Control，覆盖语义会打破它们的契约。头在进入业务链之前写出——SSE 等流式
 * 响应可能在链内提交响应，事后补头无效。</p>
 *
 * <p>不开 CSP：挂件以 iframe 被宿主页加载，CSP 的归属在前端与嵌入页，不在后端 API。
 * 开关见 {@link com.ragagent.framework.config.SecurityHeaderProperties}。</p>
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    public static final String NOSNIFF = "nosniff";

    public static final String FRAME_OPTIONS = "X-Frame-Options";

    public static final String SAMEORIGIN = "SAMEORIGIN";

    public static final String REFERRER_POLICY = "Referrer-Policy";

    public static final String NO_REFERRER = "no-referrer";

    private final boolean enabled;

    public SecurityHeadersFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        setIfAbsent(response, CONTENT_TYPE_OPTIONS, NOSNIFF);
        setIfAbsent(response, FRAME_OPTIONS, SAMEORIGIN);
        setIfAbsent(response, REFERRER_POLICY, NO_REFERRER);
        chain.doFilter(request, response);
    }

    private void setIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
