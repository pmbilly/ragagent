package com.ragagent.contract.knowledge;

import java.util.List;
import java.util.Map;


/**
 * 知识目录读写端口（L1）：知识条目与知识库两族的按 id/租户读取与增删。
 *
 * <p><b>为什么是端口</b>：会话 / 评测 / IM / 数据源等域只做"按 id 取行、按租户列行、
 * 建/删"，而实现（含 409 重复冲突、租户可见性、内嵌状态机）在知识域。
 * 实体 {@code KnowledgeRowView}/{@code KnowledgeBase} 已**归位各域**（C 批）；跨域面只搬
 * {@link KnowledgeBaseRowView} 等 L1 行视图（HANDOFF 判据 1）。</p>
 *
 * <p><b>失败语义</b>与知识域一致：id 空 / 不可见 / 状态异常 ⇒ 运行时异常原样上抛；
 * 建知识遇重复 ⇒ {@code DuplicateKnowledgeException}（409，details 带已存在文档 id）。</p>
 */
public interface KnowledgeCatalogPort {

    // ── 知识条目（读） ──

    /** 按 id 取知识（仅本人可见口径，与域实现一致）。 */
    KnowledgeRowView getKnowledgeByIdOnly(String id);

    /** 批量取知识。 */
    List<KnowledgeRowView> getKnowledgeBatch(long tenantId, List<String> ids);

    /** 批量取知识（含共享访问口径）。 */
    List<KnowledgeRowView> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids);

    /** 租户内按 id 取知识。 */
    KnowledgeRowView getKnowledgeInTenant(long tenantId, String id);

    /** 知识分页列表（关键字 / 解析状态 / 文件类型 / 目录过滤）。 */
    KnowledgePageView listKnowledge(String kbId, long page, long pageSize,
            String keyword, String parseStatus, String fileType,
            String folderPath, boolean folderPresent);

    // ── 知识条目（写） ──

    /** 删除知识（返回域实现的既定结果值）。 */
    String deleteKnowledge(String id);

    /** 从段落同步建知识（评测造数路径）。 */
    KnowledgeRowView createFromPassageSync(String kbId, List<String> passages, String channel);

    /** 从文件内容建知识（IM 附件落库路径）；重复 ⇒ 409 异常。 */
    KnowledgeRowView createFromFile(String kbId, byte[] fileContent, String fileName,
            String displayName, Object customMetadata, String channel);

    // ── 分块（读，权限解析用） ──

    /**
     * 按 id 取分块。<b>无租户过滤</b>（调用方自行做权限裁决，如 embed 公开读取的
     * 白名单校验）；软删行不可见，找不到 ⇒ 域内 not-found 运行时异常原样上抛。
     */
    ChunkRowView getChunkByIdOnly(String id);

    // ── chunk 批量读（工具/管线面，租户过滤口径与域仓储一致） ──

    /** 按 id 批量取 chunk（租户内、软删不可见）。 */
    List<ChunkRowView> listChunksById(long tenantId, List<String> ids);

    /** 按父 id 取子 chunk。 */
    List<ChunkRowView> listChunkByParentId(long tenantId, String parentId);

    /** 按父 id 批量取子 chunk。 */
    List<ChunkRowView> listChunksByParentIDs(long tenantId, List<String> parentIds);

    /** 按知识条目分页取 chunk（过滤参数与域仓储逐一同形）。 */
    ChunkPageView listPagedChunksByKnowledgeId(long tenantId, String knowledgeId, int offset,
            int limit, List<String> chunkTypes, List<String> tagIds,
            String keyword, String searchField, String sortOrder,
            String knowledgeType, Boolean isEnabled);

    // ── FAQ 目录读 ──

    /** FAQ 条目分页（过滤参数与域服务逐一同形）。 */
    FaqEntryPageView listFaqEntries(String kbId, int page, int pageSize,
            List<String> tagUuids, long legacyTagSeqId,
            String keyword, String searchField,
            String sortOrder, Boolean isEnabled);

    // ── 知识文件读（本地/云分流与错误口径由域实现承担） ──

    /** 读知识条目的存储文件（租户作用域；失败语义与域门面一致）。 */
    byte[] readKnowledgeFile(long tenantId, String filePath);

    // ── 知识库 ──

    /** 按 id 取知识库。 */
    KnowledgeBaseRowView getKnowledgeBase(String id);

    /** 建知识库。 */
    KnowledgeBaseRowView createKnowledgeBase(KnowledgeBaseRowView kb);

    /** 按创建者列知识库（null = 全部）。 */
    List<KnowledgeBaseRowView> listKnowledgeBases(String creator);

    /** 按 id 取知识库（全租户口径，与域实现一致）。 */
    KnowledgeBaseRowView getAllTenantById(String id);

    /** 删知识库。 */
    void deleteKnowledgeBase(String id);

    // ── 访问闸门与配置面（初始化向导用） ──

    /** KB 访问闸门（不可达 ⇒ 抛运行时异常，语义同知识域闸门）。 */
    KnowledgeBaseRowView requireKbAccess(String kbId);

    /** 检索驱动标识（配置面展示用）。 */
    String retrieveDriver();

    /** 按 id 更新知识库行（初始化向导的写面）。 */
    void updateKnowledgeBase(KnowledgeBaseRowView kb);

    /** 该知识库下是否已有知识文件（未删除口径）。 */
    boolean hasFiles(String kbId);

    // ── 摄取写面（数据源同步等；查询口径与域实现逐字一致） ──

    /** 按 id 取知识库行（不看删除标记、LIMIT 1）。 */
    KnowledgeBaseRowView findKnowledgeBaseRow(String kbId);

    /** 按（租户 + 库 + 元数据 datasourceId/externalId）查行（PG 口径，LIMIT 1）；无 ⇒ null。 */
    KnowledgeRowView findKnowledgeByExternalId(long tenantId, String kbId, String dataSourceId, String externalId);

    /** 按元数据某键前缀查行（PG 口径；前缀转义在实现内做）。 */
    List<KnowledgeRowView> listKnowledgeByMetadataPrefix(long tenantId, String kbId, String metadataKey, String rawPrefix);

    /** 租户 + 库下的知识行（未删除口径，非 PG 回退路径用）。 */
    List<KnowledgeRowView> listKnowledgeLive(long tenantId, String kbId);

    /** 按（库 + 文件哈希 + 文件类型）查重（未删除口径）；无 ⇒ null。 */
    KnowledgeRowView findKnowledgeByHash(String kbId, String fileHash, String fileType);

    /** 落库新知识行。 */
    void insertKnowledge(KnowledgeRowView knowledge);

    /** 存知识文件（本地/云分流由域内门面决定），返回存储引用。 */
    String saveKnowledgeFile(long tenantId, String knowledgeId, String fileName, byte[] content);

    /** 删知识文件（best-effort 语义与域内门面一致）。 */
    void deleteKnowledgeFile(long tenantId, String knowledgeId, String filePath);

    /** 更新知识行的元数据（含 updatedAt）。 */
    void updateKnowledgeMetadataRow(KnowledgeRowView knowledge, Map<String, String> metadata);

    /** 软删知识 + 其 chunk（置 deletedAt）+ 清文件（best-effort）。 */
    void softDeleteKnowledgeFull(String knowledgeId, long tenantId, java.time.OffsetDateTime now);

    /** 硬删知识行（按 id + 租户）。 */
    void hardDeleteKnowledge(String knowledgeId, long tenantId);

    /** 硬删知识行（按租户 + id 集合）。 */
    void hardDeleteKnowledgeBatch(long tenantId, List<String> knowledgeIds);

    /** 建知识标签（校验与排序口径同域实现）。 */
    KnowledgeTagView createKnowledgeTag(String kbId, String name, String color, int sortOrder);

    /** 按（租户 + 库 + 名）查标签；无 ⇒ null。 */
    KnowledgeTagView findKnowledgeTagByName(long tenantId, String kbId, String name);
}
