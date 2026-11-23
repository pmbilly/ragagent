package com.ragagent.model.controller.vo;

import java.util.Map;

/**
 * 模型调试结果的 HTTP 响应体。
 *
 * <p>运行时错误不是 HTTP 错误：{@code ok=false} + {@code error} 原文，HTTP 恒 200。
 * {@code rawResponse} 多态（对象 / 数组 / 文本），模型调用未发生时是 null。</p>
 */
public record ModelDebugRespVo(
        boolean ok,
        long elapsedMs,
        String error,
        Map<String, Object> request,
        Object rawResponse,
        Map<String, Object> observations) {
}
