package com.ragagent.storage.assembler;

import com.ragagent.storage.api.StorageConnectivityPort;
import com.ragagent.storage.controller.vo.StorageCheckRespVo;
import com.ragagent.storage.controller.vo.StorageEngineStatusRespVo;
import java.util.List;

/** 存储引擎探测结果到 HTTP VO 的装配。 */
public final class StorageEngineAssembler {

    private StorageEngineAssembler() {
    }

    /** 装配状态页；引擎顺序由端口给定，原样保留。 */
    public static StorageEngineStatusRespVo toStatusVo(StorageConnectivityPort.Status status) {
        if (status == null) {
            return null;
        }
        return new StorageEngineStatusRespVo(
                toItemVoList(status.engines()),
                status.allowedProviders(),
                status.minioEnvAvailable());
    }

    /** 装配连通性检测结果。 */
    public static StorageCheckRespVo toCheckVo(StorageConnectivityPort.CheckResult result, boolean bucketCreated) {
        if (result == null) {
            return null;
        }
        return new StorageCheckRespVo(result.ok(), result.message(), bucketCreated);
    }

    private static List<StorageEngineStatusRespVo.StorageEngineItemVo> toItemVoList(
            List<StorageConnectivityPort.EngineStatus> engines) {
        if (engines == null) {
            return null;
        }
        return engines.stream()
                .map(e -> new StorageEngineStatusRespVo.StorageEngineItemVo(
                        e.provider(), e.allowed(), e.configured(), e.description()))
                .toList();
    }
}
