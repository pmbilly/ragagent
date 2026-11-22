package com.ragagent.storage.assembler;

import com.ragagent.storage.controller.vo.StorageBackendRespVo;
import com.ragagent.storage.dto.StorageBackendResponse;
import java.util.List;

/** 存储后端响应 DTO 到 HTTP VO 的装配。 */
public final class StorageBackendAssembler {

    private StorageBackendAssembler() {
    }

    /** 装配单个后端；配置节点原样透传。 */
    public static StorageBackendRespVo toVo(StorageBackendResponse src) {
        if (src == null) {
            return null;
        }
        return new StorageBackendRespVo(
                src.id,
                src.tenantId,
                src.name,
                src.provider,
                src.config,
                src.source,
                src.status,
                src.legacyAlias,
                src.createdAt,
                src.updatedAt,
                src.deletedAt);
    }

    /** 批量装配，保持原有顺序。 */
    public static List<StorageBackendRespVo> toVoList(List<StorageBackendResponse> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(StorageBackendAssembler::toVo).toList();
    }
}
