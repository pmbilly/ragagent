package com.ragagent.storage.controller.vo;

import java.util.List;

/**
 * 存储引擎状态清单的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：engines / allowedProviders / minioEnvAvailable。</p>
 */
public record StorageEngineStatusRespVo(
        List<StorageEngineItemVo> engines,
        List<String> allowedProviders,
        boolean minioEnvAvailable) {

    /** 单个存储引擎状态：{@code available} 即部署配置是否齐备。 */
    public record StorageEngineItemVo(
            String name,
            boolean allowed,
            boolean available,
            String description) {
    }
}
