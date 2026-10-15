package com.ragagent.common.storage;

import java.util.List;

/**
 * 存储后端的运维面（L1）：连通性探测、错误脱敏、按租户列后端。
 *
 * <p><b>为什么是端口</b>：系统域的系统信息与"存储连通性检测"页需要这些能力，但探测逻辑
 * （各云 SDK）与元数据读取属存储域实现。端口只承载 {@link StorageBackend}（已上提 L1 的载荷）。</p>
 */
public interface StorageBackendPort {

    /** 连通性探测：不可达 ⇒ 抛 {@link RuntimeException}（消息为 SDK 原文，供调用方分派文案）。 */
    void test(StorageBackend backend);

    /** 连通性错误信息脱敏（抹掉凭证、内网地址等敏感片段）。 */
    String sanitizeConnectivity(String message);

    /** 租户的存储后端清单（顺序与仓储一致）。 */
    List<StorageBackend> list(long tenantId);
}
