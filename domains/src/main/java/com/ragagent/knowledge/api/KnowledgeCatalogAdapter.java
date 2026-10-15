package com.ragagent.knowledge.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.knowledge.KnowledgeCatalogPort;
import com.ragagent.common.knowledge.domain.Chunk;
import com.ragagent.common.knowledge.domain.ChunkPage;
import com.ragagent.common.knowledge.domain.FaqEntryPage;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.service.faq.FaqEntryQueryService;
import com.ragagent.knowledge.service.management.KnowledgeBaseService;
import com.ragagent.knowledge.service.management.KnowledgeService;
import com.ragagent.knowledge.storage.TenantFileStorage;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import com.ragagent.knowledge.mapper.KnowledgeBaseMapper;
import com.ragagent.knowledge.domain.access.KnowledgeAccessGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.platform.persistence.PgJsonTypeHandler;
import com.ragagent.knowledge.service.management.KnowledgeTagService;
import com.ragagent.knowledge.mapper.ChunkMapper;
import com.ragagent.knowledge.domain.KnowledgeTag;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Map;
import java.util.ArrayList;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import com.ragagent.knowledge.repository.KnowledgeTagRepository;
import com.ragagent.platform.web.JsonMappers;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.contract.knowledge.KnowledgeTagView;

/**
 * {@link KnowledgeCatalogPort} 的知识域实现：读写逐字转发两个门面服务，
 * 不新增缓冲、不改异常口径（409 重复冲突等由服务原样抛出）。
 */
@Component
public class KnowledgeCatalogAdapter implements KnowledgeCatalogPort {

    private static final ObjectMapper METADATA_MAPPER = JsonMappers.lenient();


    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessGuard accessGuard;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeTagService knowledgeTagService;
    private final KnowledgeTagRepository knowledgeTagRepository;
    private final TenantFileStorage tenantFileStorage;
    private final ChunkRepository chunkRepository;
    private final FaqEntryQueryService faqEntryQueryService;

    public KnowledgeCatalogAdapter(KnowledgeService knowledgeService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeAccessGuard accessGuard,
                                   KnowledgeBaseMapper knowledgeBaseMapper,
                                   KnowledgeMapper knowledgeMapper,
                                   ChunkMapper chunkMapper,
                                   KnowledgeTagService knowledgeTagService,
                                   KnowledgeTagRepository knowledgeTagRepository,
                                   TenantFileStorage tenantFileStorage,
                                   ChunkRepository chunkRepository,
                                   FaqEntryQueryService faqEntryQueryService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.accessGuard = accessGuard;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeTagService = knowledgeTagService;
        this.knowledgeTagRepository = knowledgeTagRepository;
        this.tenantFileStorage = tenantFileStorage;
        this.chunkRepository = chunkRepository;
        this.faqEntryQueryService = faqEntryQueryService;
    }

    @Override
    public Knowledge getKnowledgeByIdOnly(String id) {
        return knowledgeService.getKnowledgeByIdOnly(id);
    }

    @Override
    public List<Knowledge> getKnowledgeBatch(long tenantId, List<String> ids) {
        return knowledgeService.getKnowledgeBatch(tenantId, ids);
    }

    @Override
    public List<Knowledge> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids) {
        return knowledgeService.getKnowledgeBatchWithSharedAccess(tenantId, ids);
    }

    @Override
    public Knowledge getKnowledgeInTenant(long tenantId, String id) {
        return knowledgeService.getKnowledgeInTenant(tenantId, id);
    }

    @Override
    public Page<Knowledge> listKnowledge(String kbId, long page, long pageSize,
            String keyword, String parseStatus, String fileType,
            String folderPath, boolean folderPresent) {
        return knowledgeService.listKnowledge(kbId, page, pageSize, keyword, parseStatus,
                fileType, folderPath, folderPresent);
    }

    @Override
    public String deleteKnowledge(String id) {
        return knowledgeService.deleteKnowledge(id);
    }

    @Override
    public Knowledge createFromPassageSync(String kbId, List<String> passages, String channel) {
        return knowledgeService.createFromPassageSync(kbId, passages, channel);
    }

    @Override
    public Knowledge createFromFile(String kbId, byte[] fileContent, String fileName,
            String displayName, JsonNode customMetadata, String channel) {
        return knowledgeService.createFromFile(kbId, fileContent, fileName, displayName,
                customMetadata, channel);
    }

    @Override
    public Chunk getChunkByIdOnly(String id) {
        return chunkRepository.getChunkByIdOnly(id);
    }

    @Override
    public List<Chunk> listChunksById(long tenantId, List<String> ids) {
        return chunkRepository.listChunksById(tenantId, ids);
    }

    @Override
    public List<Chunk> listChunkByParentId(long tenantId, String parentId) {
        return chunkRepository.listChunkByParentId(tenantId, parentId);
    }

    @Override
    public List<Chunk> listChunksByParentIDs(long tenantId, List<String> parentIds) {
        return chunkRepository.listChunksByParentIDs(tenantId, parentIds);
    }

    @Override
    public ChunkPage listPagedChunksByKnowledgeId(long tenantId, String knowledgeId,
            int offset, int limit, List<String> chunkTypes, List<String> tagIds,
            String keyword, String searchField, String sortOrder,
            String knowledgeType, Boolean isEnabled) {
        return chunkRepository.listPagedChunksByKnowledgeId(tenantId, knowledgeId, offset,
                limit, chunkTypes, tagIds, keyword, searchField, sortOrder,
                knowledgeType, isEnabled);
    }

    @Override
    public FaqEntryPage listFaqEntries(String kbId, int page, int pageSize,
            List<String> tagUuids, long legacyTagSeqId,
            String keyword, String searchField, String sortOrder, Boolean isEnabled) {
        return faqEntryQueryService.listEntries(kbId, page, pageSize, tagUuids,
                legacyTagSeqId, keyword, searchField, sortOrder, isEnabled);
    }

    @Override
    public byte[] readKnowledgeFile(long tenantId, String filePath) {
        return tenantFileStorage.read(tenantId, filePath);
    }

    @Override
    public KnowledgeBase getKnowledgeBase(String id) {
        return knowledgeBaseService.getKnowledgeBase(id);
    }

    @Override
    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        return knowledgeBaseService.createKnowledgeBase(kb);
    }

    @Override
    public List<KnowledgeBase> listKnowledgeBases(String creator) {
        return knowledgeBaseService.listKnowledgeBases(creator);
    }

    @Override
    public KnowledgeBase getAllTenantById(String id) {
        return knowledgeBaseService.getAllTenantById(id);
    }

    @Override
    public void deleteKnowledgeBase(String id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
    }

    @Override
    public KnowledgeBase requireKbAccess(String kbId) {
        return accessGuard.requireKbAccess(kbId);
    }

    @Override
    public String retrieveDriver() {
        return knowledgeBaseService.retrieveDriver();
    }

    @Override
    public void updateKnowledgeBase(KnowledgeBase kb) {
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public boolean hasFiles(String kbId) {
        Long tid = TenantContext.currentTenantId();
        Long count = knowledgeMapper.selectCount(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(tid != null, Knowledge::getTenantId, tid)
                .isNull(Knowledge::getDeletedAt));
        return count != null && count > 0;
    }

    // ── 摄取写面（SQL 与查询口径自 datasource 侧 verbatim 搬入） ──

    @Override
    public KnowledgeBase findKnowledgeBaseRow(String kbId) {
        return knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
    }

    @Override
    public Knowledge findKnowledgeByExternalId(long tenantId, String kbId, String dataSourceId, String externalId) {
        List<Knowledge> rows = knowledgeMapper.selectList(
                new LambdaQueryWrapper<Knowledge>()
                        .eq(Knowledge::getTenantId, tenantId)
                        .eq(Knowledge::getKnowledgeBaseId, kbId)
                        .apply("deleted_at IS NULL")
                        .apply("metadata->>'datasourceId' = {0}", dataSourceId)
                        .apply("metadata->>'externalId' = {0}", externalId)
                        .last("LIMIT 1"));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Knowledge> listKnowledgeByMetadataPrefix(long tenantId, String kbId,
            String metadataKey, String rawPrefix) {
        List<Knowledge> rows = knowledgeMapper.selectList(
                new LambdaQueryWrapper<Knowledge>()
                        .eq(Knowledge::getTenantId, tenantId)
                        .eq(Knowledge::getKnowledgeBaseId, kbId)
                        .apply("deleted_at IS NULL")
                        .apply("metadata->>'" + metadataKey.replace("'", "''") + "' LIKE {0} ESCAPE '\\\\'",
                                escapeLike(rawPrefix) + "%"));
        return rows == null ? new ArrayList<>() : rows;
    }

    @Override
    public List<Knowledge> listKnowledgeLive(long tenantId, String kbId) {
        List<Knowledge> rows = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getTenantId, tenantId)
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .isNull(Knowledge::getDeletedAt));
        return rows == null ? new ArrayList<>() : rows;
    }

    @Override
    public Knowledge findKnowledgeByHash(String kbId, String fileHash, String fileType) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getFileHash, fileHash)
                .eq(Knowledge::getFileType, fileType)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    @Override
    public void insertKnowledge(Knowledge knowledge) {
        knowledgeMapper.insert(knowledge);
    }

    @Override
    public String saveKnowledgeFile(long tenantId, String knowledgeId, String fileName, byte[] content) {
        return tenantFileStorage.save(tenantId, knowledgeId, fileName, content);
    }

    @Override
    public void deleteKnowledgeFile(long tenantId, String knowledgeId, String filePath) {
        tenantFileStorage.delete(tenantId, knowledgeId, filePath);
    }

    @Override
    public void updateKnowledgeMetadataRow(Knowledge knowledge, Map<String, String> metadata) {
        LambdaUpdateWrapper<Knowledge> uw = new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledge.getId())
                .eq(Knowledge::getTenantId, knowledge.getTenantId())
                .set(Knowledge::getMetadata, metadataNode(metadata), "typeHandler="
                        + PgJsonTypeHandler.class.getName())
                .set(Knowledge::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC));
        knowledgeMapper.update(null, uw);
    }

    @Override
    public void softDeleteKnowledgeFull(String knowledgeId, long tenantId, OffsetDateTime now) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledgeId).eq(Knowledge::getTenantId, tenantId)
                .set(Knowledge::getDeletedAt, now));
        chunkMapper.update(null, new LambdaUpdateWrapper<Chunk>()
                .eq(Chunk::getKnowledgeId, knowledgeId).set(Chunk::getDeletedAt, now));
        Knowledge row = knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledgeId)
                .eq(Knowledge::getTenantId, tenantId)
                .last("LIMIT 1"));
        tenantFileStorage.delete(tenantId, knowledgeId, row == null ? null : row.getFilePath());
    }

    @Override
    public void hardDeleteKnowledge(String knowledgeId, long tenantId) {
        knowledgeMapper.delete(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledgeId)
                .eq(Knowledge::getTenantId, tenantId));
    }

    @Override
    public void hardDeleteKnowledgeBatch(long tenantId, List<String> knowledgeIds) {
        knowledgeMapper.delete(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getTenantId, tenantId)
                .in(Knowledge::getId, knowledgeIds));
    }

    @Override
    public KnowledgeTagView createKnowledgeTag(String kbId, String name, String color, int sortOrder) {
        return tagView(knowledgeTagService.createTag(kbId, name, color, sortOrder));
    }

    /** 实体 → L1 视图（域内投影；空透传）。 */
    private static KnowledgeTagView tagView(KnowledgeTag t) {
        if (t == null) {
            return null;
        }
        return new KnowledgeTagView(t.getId(), t.getKnowledgeBaseId(), t.getTenantId(),
                t.getName(), t.getColor(), t.getSortOrder());
    }

    @Override
    public KnowledgeTagView findKnowledgeTagByName(long tenantId, String kbId, String name) {
        return tagView(knowledgeTagRepository.getByName(tenantId, kbId, name));
    }

    /** metadata map → JSON 节点（自 datasource 侧 verbatim 搬入）。 */
    private static JsonNode metadataNode(Map<String, String> metadata) {
        ObjectNode node = METADATA_MAPPER.createObjectNode();
        if (metadata != null) {
            for (Map.Entry<String, String> e : metadata.entrySet()) {
                node.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        return node;
    }

    /** SQL LIKE 转义（自 datasource 侧 verbatim 搬入）。 */
    static String escapeLike(String v) {
        return v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
