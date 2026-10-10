package com.ragagent.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 把「本次请求命中 {@link ApiResult} 控制器」写进请求属性，供 {@code GlobalExceptionHandler}
 * 决定错误形态。注册见 {@code WebConfig#addInterceptors}（order = -100，早于 RBAC / API-Key 门禁）。
 */
public class ApiResultInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod
                && ApiResultSupport.isAnnotated(handlerMethod.getBeanType())) {
            ApiResultSupport.markActive(request);
        }
        return true;
    }
}
