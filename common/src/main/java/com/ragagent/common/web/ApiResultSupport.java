package com.ragagent.common.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * {@link ApiResult} 的两处共享判定：命中路由的控制器是否标注、本次请求是否已被打标。
 *
 * <p><b>为什么需要「打标」</b>：成功侧 {@link ApiResultAdvice} 直接看控制器类即可；但错误侧在
 * {@code GlobalExceptionHandler} 里，异常处理器的方法参数是自己的（拿不到原始 HandlerMethod），
 * 因此在 {@link ApiResultInterceptor#preHandle} 里把判定结果写进请求属性，异常处理器再读它。</p>
 *
 * <p>拦截器 order = -100 ⇒ 打标早于 RBAC / API-Key 门禁 ⇒ 被门禁拒绝的请求同样拿到新错误形态。
 * 反例（本机制的边界）：{@code AuthFilter} 这类 **Filter** 在 DispatcherServlet 之前就写出响应，
 * 本机制覆盖不到，其形态保持不变（见约定文档的「已知例外」）。</p>
 */
public final class ApiResultSupport {

    /** 请求属性名（本包内使用）。 */
    static final String REQUEST_ATTRIBUTE = ApiResultSupport.class.getName() + ".ACTIVE";

    private ApiResultSupport() {
    }

    /** 类型（含元注解）上是否有 {@link ApiResult}。 */
    public static boolean isAnnotated(Class<?> type) {
        return type != null && AnnotatedElementUtils.hasAnnotation(type, ApiResult.class);
    }

    /** 本请求是否落在 {@link ApiResult} 控制器上（由 {@link ApiResultInterceptor} 写入）。 */
    public static boolean isActive(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE));
    }

    static void markActive(HttpServletRequest request) {
        request.setAttribute(REQUEST_ATTRIBUTE, Boolean.TRUE);
    }
}
