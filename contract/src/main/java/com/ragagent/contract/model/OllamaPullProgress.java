package com.ragagent.contract.model;

/**
 * Ollama 拉取进度（契约面形状，原为 {@code JsonNode} 载荷）。
 *
 * <p>R2c-3d：管理端点的进度回调原先直接吃 Ollama 的原始 JSON（业务域自己 `path("total")`）；
 * 现由实现侧解析成记录，域侧只做展示（百分比与文案）。</p>
 */
public record OllamaPullProgress(long completed, long total, String status) {
}
