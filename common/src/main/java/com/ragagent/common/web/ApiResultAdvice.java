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
        if (returnType.getParameterType() == void.class) {
            // void 端点自己写响应（外部平台回调 / 流式）⇒ 不能包：会把 null 包成外壳，
            // 与端点已写入的裸响应**双写** ✗（B191：ImCallbackController 属于这一类）。
            return false;
        }
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
        if (body instanceof byte[] || body instanceof org.springframework.core.io.Resource) {
            // 二进制 / 资源载荷直接放行（B197）：导出类端点常把 JSON/CSV **文本**配成 application/json
            // 或 text/csv ✗ —— 内容类型判不出来，若包壳则 byte[] 转换器抛 ClassCastException ⇒ 500 ✗
            // （实测：FaqController.exportEntries 的 `?format=json` 分支，code 1007 ✓）
            return body;
        }
        return ApiResponse.ok(body);
    }
}
