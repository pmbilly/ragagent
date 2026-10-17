package com.ragagent.contract.storage;

import java.util.Optional;

/**
 * 资源目录读/绑定面（L1）：按 {@code resource://} 引用解析资源归属，并把资源绑到属主。
 *
 * <p><b>面向的调用方</b>：会话域 agent 抓取页（{@code web://} 快照）——读时需确认
 * "资源属于本租户"、写时需把资源绑到 assistant 消息。此前直接依赖存储域的目录服务与资源实体，
 * 本端口只给最小视图（{@code ResourceView}）与两个动作。</p>
 */
public interface ResourceCatalogPort {

    /** 按引用解析资源；不可解析 ⇒ {@link Optional#empty()}。 */
    Optional<ResourceView> resolve(String reference);

    /** 建立绑定：资源 → 属主（如 {@code web_page} → assistant 消息）。 */
    void bind(String reference, String ownerType, String ownerId, String relation);

    /**
     * 物理路径注册为稳定资源引用（已注册则复用既有句柄），返回可绑定的
     * {@code resource://} 引用。解析器抽取图片的服务端引用由此登记后才能在文件代理下取图。
     */
    String register(long tenantId, String physicalPath, ResourceMeta meta);

    /** 按物理路径软删资源行（不存在则忽略）。 */
    void markDeletedByLocation(long tenantId, String physicalPath);

    /**
     * 注册元数据（可空字段按空串/0 落库）。
     *
     * @param kind        资源类别（如 {@code image}）
     * @param mimeType    MIME 类型
     * @param originalName 原始文件名
     * @param size        字节数
     * @param contentHash 内容哈希（可空）
     * @param temporary   临时资源（可随会话过期）
     */
    record ResourceMeta(String kind, String mimeType, String originalName, long size,
                        String contentHash, boolean temporary) {
    }

    /**
     * 资源最小视图。
     *
     * @param id       资源 id（绑定存在性检查用）
     * @param tenantId 资源属主租户（会话授权比对用）
     */
    record ResourceView(String id, long tenantId) {
    }
}
