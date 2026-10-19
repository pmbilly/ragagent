package com.ragagent.contract.storage;

import java.util.List;
import java.util.Map;

/**
 * 存储引擎可用性与连通性探测端口：信息页需要"哪些引擎可用 / 某套配置能不能连上"。
 *
 * <p>探测逻辑（7 厂商必填校验、SSRF 拦截、SDK 错误文案）是存储域知识，实现归
 * {@code storage}；调用方只认本端口，不 import 存储域实现。</p>
 *
 * <p>配置以纯 JDK 值（{@code Map}）传入——契约面不持有 Jackson 节点类型；
 * 边界转换由提供方完成。</p>
 */
public interface StorageConnectivityPort {

    /** 一次探测的结论。 */
    record CheckResult(boolean ok, String message) {
    }

    /** 单个引擎在部署/租户维度的可用性。 */
    record EngineStatus(String provider, boolean allowed, boolean configured, String description) {
    }

    /** 状态页整体结果（{@code engines} 按展示序）。 */
    record Status(List<EngineStatus> engines, List<String> allowedProviders, boolean minioEnvAvailable) {
    }

    /** 按展示序合成状态页。 */
    Status status(long tenantId);

    /**
     * 探测指定 provider 的配置。
     *
     * @param provider 引擎标识（local/minio/cos/tos/s3/oss/ks3/obs）
     * @param config   该引擎的配置节点（可为 null）
     * @param tenantId 当前租户（0 表示无租户上下文）
     */
    CheckResult check(String provider, Map<String, Object> config, long tenantId);

    /** provider 是否在部署白名单内。 */
    boolean isAllowed(String provider);

    /** 租户是否已配置 MinIO（remote 三要素齐全，或部署 env 齐备）。 */
    boolean isMinioConfigured(long tenantId);
}
