package com.ragagent.storage.controller.vo;

/**
 * 存储连通性检测结果的 HTTP 响应体。
 *
 * <p>{@code bucketCreated} 仅在成功建桶时为 true；检测端点恒为 false。</p>
 */
public record StorageCheckRespVo(
        boolean ok,
        String message,
        boolean bucketCreated) {
}
