package com.ragagent.common.knowledge;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.knowledge.domain.Chunk;
import com.ragagent.common.knowledge.domain.ChunkPage;
import com.ragagent.common.knowledge.domain.FaqEntryPage;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeBase;

/**
 * 知识目录读写端口（L1）：知识条目与知识库两族的按 id/租户读取与增删。
 *
 * <p><b>为什么是端口</b>：会话 / 评测 / IM / 数据源等域只做"按 id 取行、按租户列行、
 * 建/删"，而实现（含 409 重复冲突、租户可见性、内嵌状态机）在知识域。
 * 实体 {@link Knowledge} / {@link KnowledgeBase} 已上提 L1，端口直接搬运它们，无需视图。</p>
 *
 * <p><b>失败语义</b>与知识域一致：id 空 / 不可见 / 状态异常 ⇒ 运行时异常原样上抛；
 * 建知识遇重复 ⇒ {@code DuplicateKnowledgeException}（409，details 带已存在文档 id）。</p>
 */
public interface KnowledgeCatalogPort {

    // ── 知识条目（读） ──

    /** 按 id 取知识（仅本人可见口径，与域实现一致）。 */
    Knowledge getKnowledgeByIdOnly(String id);

    /** 批量取知识。 */
    List<Knowledge> getKnowledgeBatch(long tenantId, List<String> ids);

    /** 批量取知识（含共享访问口径）。 */
    List<Knowledge> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids);

    /** 租户内按 id 取知识。 */
    Knowledge getKnowledgeInTenant(long tenantId, String id);

    /** 知识分页列表（关键字 / 解析状态 / 文件类型 / 目录过滤）。 */
    Page<Knowledge> listKnowledge(String kbId, long page, long pageSize,
            String keyword, String parseStatus, String fileType,
            String folderPath, boolean folderPresent);

    // ── 知识条目（写） ──

    /** 删除知识（返回域实现的既定结果值）。 */
    String deleteKnowledge(String id);

    /** 从段落同步建知识（评测造数路径）。 */
    Knowledge createFromPassageSync(String kbId, List<String> passages, String channel);

    /** 从文件内容建知识（IM 附件落库路径）；重复 ⇒ 409 异常。 */
    Knowledge createFromFile(String kbId, byte[] fileContent, String fileName,
            String displayName, JsonNode customMetadata, String channel);

    // ── 分块（读，权限解析用） ──

    /**
     * 按 id 取分块。<b>无租户过滤</b>（调用方自行做权限裁决，如 embed 公开读取的
     * 白名单校验）；软删行不可见，找不到 ⇒ 域内 not-found 运行时异常原样上抛。
     */
    Chunk getChunkByIdOnly(String id);

    // ── chunk 批量读（工具/管线面，租户过滤口径与域仓储一致） ──

    /** 按 id 批量取 chunk（租户内、软删不可见）。 */
    List<Chunk> listChunksById(long tenantId, List<String> ids);

    /** 按父 id 取子 chunk。 */
    List<Chunk> listChunkByParentId(long tenantId, String parentId);

    /** 按父 id 批量取子 chunk。 */
    List<Chunk> listChunksByParentIDs(long tenantId, List<String> parentIds);

    /** 按知识条目分页取 chunk（过滤参数与域仓储逐一同形）。 */
    ChunkPage listPagedChunksByKnowledgeId(long tenantId, String knowledgeId, int offset,
            int limit, List<String> chunkTypes, List<String> tagIds,
            String keyword, String searchField, String sortOrder,
            String knowledgeType, Boolean isEnabled);

    // ── FAQ 目录读 ──

    /** FAQ 条目分页（过滤参数与域服务逐一同形）。 */
    FaqEntryPage listFaqEntries(String kbId, int page, int pageSize,
            List<String> tagUuids, long legacyTagSeqId,
            String keyword, String searchField,
            String sortOrder, Boolean isEnabled);

    // ── 知识文件读（本地/云分流与错误口径由域实现承担） ──

    /** 读知识条目的存储文件（租户作用域；失败语义与域门面一致）。 */
    byte[] readKnowledgeFile(long tenantId, String filePath);

    // ── 知识库 ──

    /** 按 id 取知识库。 */
    KnowledgeBase getKnowledgeBase(String id);

    /** 建知识库。 */
    KnowledgeBase createKnowledgeBase(KnowledgeBase kb);

    /** 按创建者列知识库（null = 全部）。 */
    List<KnowledgeBase> listKnowledgeBases(String creator);

    /** 按 id 取知识库（全租户口径，与域实现一致）。 */
    KnowledgeBase getAllTenantById(String id);

    /** 删知识库。 */
    void deleteKnowledgeBase(String id);
}
