package com.ragagent.storage.controller.vo;

import java.util.List;

/**
 * 存储后端清单的 HTTP 响应体：{@code {items, defaultStorageBackendId}}。
 *
 * <p>后者在无租户上下文时为 null，键仍恒输出。</p>
 */
public record StorageBackendListRespVo(
        List<StorageBackendRespVo> items,
        String defaultStorageBackendId) {
}
