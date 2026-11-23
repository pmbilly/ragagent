package com.ragagent.model.controller.vo;

import java.util.List;
import java.util.Map;

/**
 * 模型提供方列表项的 HTTP 响应体。
 *
 * <p>{@code defaultUrls} 为 map（字母序输出），{@code modelTypes} 顺序 = 注册表声明序。</p>
 */
public record ModelProviderRespVo(
        String value,
        String label,
        String description,
        Map<String, String> defaultUrls,
        List<String> modelTypes) {
}
