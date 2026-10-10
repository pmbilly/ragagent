package com.ragagent.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 成功侧统一外壳：{@link ApiResult} 控制器的方法返回值一律包成 {@link ApiResponse}。
 *
 * <p>三个细节：</p>
 * <ol>
 *   <li>已是 {@link ApiResponse} 的原样放行 —— 控制器需要自定义成功文案时自己返回它，幂等不乱套。</li>
 *   <li>非 JSON 端点（文件下载 / text / SSE）跳过：外壳只在 {@code application/json} 上成立。</li>
 *   <li>{@code ResponseEntity} 的自定义状态码（如 201）不受影响 —— 只换 body，不碰状态。</li>
 * </ol>
 */
@RestControllerAdvice
public class ApiResultAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResultSupport.isAnnotated(returnType.getContainingClass());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        if (selectedContentType != null && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
