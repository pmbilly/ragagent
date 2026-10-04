package com.ragagent.knowledge.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.knowledge.task.KnowledgeProcessingQueue;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.mapper.ChunkMapper;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import com.ragagent.knowledge.security.ChunkAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量面：批量删除 / 批量重解析 / 重建索引 / 清空 KB 内容。
 * <p>复位/全列写复用 {@link KnowledgeFileService}/{@link KnowledgeParseService}（同包开放），
 * moving 状态防线复用 {@link ChunkAccessGuard#rejectMovingKnowledge}；
 * 门面 helper（requireKb/getKnowledge/tenantId）下沉在 {@link KnowledgeAccessHelper}，直接依赖不回注门面（M2 解环）。</p>
 */
@Service
public class KnowledgeBatchOpsService {

    private final KnowledgeMapper knowledgeMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeProcessingQueue worker;
    private final KnowledgeFileService knowledgeFileService;
    private final KnowledgeAccessHelper access;

    public KnowledgeBatchOpsService(KnowledgeMapper knowledgeMapper,
                                    ChunkMapper chunkMapper,
                                    KnowledgeProcessingQueue worker,
                                    KnowledgeFileService knowledgeFileService,
                                    KnowledgeAccessHelper access) {
        this.knowledgeMapper = knowledgeMapper;
        this.chunkMapper = chunkMapper;
        this.worker = worker;
        this.knowledgeFileService = knowledgeFileService;
        this.access = access;
    }

    // ── 批量删除 / 批量重解析 / 清空（任务队列 → 同步尽力而为，响应契约一致） ──

    /**
     * Java 同步软删（chunk + knowledge + 本地文件），HTTP 契约（task_id/文案）一致。
     * 注意调用方已做过 RejectMoving/kb 归属校验。
     */
    @Transactional
    public String batchDeleteKnowledge(String kbId, List<String> ids) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (String id : ids) {
            knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                    .eq(Knowledge::getId, id)
                    .isNull(Knowledge::getDeletedAt)
                    .set(Knowledge::getDeletedAt, now));
            chunkMapper.update(null, new LambdaUpdateWrapper<Chunk>()
                    .eq(Chunk::getKnowledgeId, id)
                    .set(Chunk::getDeletedAt, now));
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 逐条 reset 到 pending + 入队。
     * 调用方已完成 requireKnowledgeInKB / RejectMoving 校验。
     */
    public String batchReparseKnowledge(String kbId, List<String> ids) {
        KnowledgeBase kb = access.requireKb(kbId);
        for (String id : ids) {
            Knowledge k = access.getKnowledge(id);
            KnowledgeParseService.resetKnowledgeForReparse(k, kb);
            knowledgeFileService.updateKnowledgeRow(k, k.getMetadata());
            worker.enqueue(k.getId());
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 重建知识库索引：索引策略
     * （vector/keyword/wiki/graph）变更后对 KB 内全部知识重跑处理管线，使 chunk/
     * 向量/图谱与新策略一致。复用 reparse 的复位与入队路径（docreader 重解析在
     * @return 提交重建的知识条数（document_count）
     */
    public int rebuildKnowledgeBaseIndex(String kbId) {
        KnowledgeBase kb = access.requireKb(kbId);
        List<Knowledge> rows = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getTenantId, KnowledgeService.tenantId())
                .isNull(Knowledge::getDeletedAt));
        for (Knowledge k : rows) {
            KnowledgeParseService.resetKnowledgeForReparse(k, kb);
            knowledgeFileService.updateKnowledgeRow(k, k.getMetadata());
            worker.enqueue(k.getId());
        }
        return rows.size();
    }

    /**
     * 列表计数），录制的两次连续 clear 都是 "task submitted" + 相同计数（worker 尚未
     * 动行）——Java 用 parse_status='deleting' 标记 + 计数维持这个窗口（行为收敛：
     * 后续读路径对 KB2 无感知；真正的回收与既有 deleteKnowledge 语义一致地缺位，
     * 见类注释已知差异 ①）。
     * @return 本次列入清理的条数
     */
    @Transactional
    public int clearKnowledgeBaseContents(String kbId) {
        List<Knowledge> rows = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getTenantId, KnowledgeService.tenantId())
                .isNull(Knowledge::getDeletedAt));
        for (Knowledge row : rows) {
            ChunkAccessGuard.rejectMovingKnowledge(row);
        }
        if (rows.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .in(Knowledge::getId, rows.stream().map(Knowledge::getId).toList())
                .set(Knowledge::getParseStatus, Knowledge.PARSE_DELETING)
                .set(Knowledge::getUpdatedAt, now));
        return rows.size();
    }

    /** 内部：worker 使用的按 id 加载（无租户条件，任务可能跨请求线程） */
    public Knowledge loadById(String id) {
        return knowledgeMapper.selectById(id);
    }

    public void updateStatus(String id, String parseStatus, String errorMessage, Boolean enable) {
        LambdaUpdateWrapper<Knowledge> uw = new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id);
        uw.set(Knowledge::getParseStatus, parseStatus);
        uw.set(Knowledge::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC));
        if (errorMessage != null) {
            uw.set(Knowledge::getErrorMessage, errorMessage);
        }
        if (enable != null) {
            uw.set(Knowledge::getEnableStatus, enable ? "enabled" : "disabled");
            uw.set(Knowledge::getProcessedAt, OffsetDateTime.now(ZoneOffset.UTC));
        }
        knowledgeMapper.update(null, uw);
    }
}
