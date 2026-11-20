package com.ragagent.knowledge.assembler;

import com.ragagent.knowledge.controller.vo.*;
import com.ragagent.knowledge.dto.TaskIdResponse;
import com.ragagent.knowledge.dto.chunk.*;
import com.ragagent.knowledge.dto.doc.*;
import com.ragagent.knowledge.dto.kb.*;
import com.ragagent.knowledge.dto.tag.*;
import java.util.List;
import java.util.function.Function;

/** 知识运营与检索相关响应的装配：只做字段搬运。 */
public final class KnowledgeAssembler {

    private KnowledgeAssembler() {
    }

    /** {@code BatchTaskData} -> {@code BatchTaskRespVo}。 */
    public static BatchTaskRespVo toVo(BatchTaskData src) {
        if (src == null) {
            return null;
        }
        return new BatchTaskRespVo(
                src.deletedCount(),
                src.taskId());
    }

    /** {@code FolderMoveResponse} -> {@code FolderMoveRespVo}。 */
    public static FolderMoveRespVo toVo(FolderMoveResponse src) {
        if (src == null) {
            return null;
        }
        return new FolderMoveRespVo(
                src.folderPath(),
                src.movedCount());
    }

    /** {@code KnowledgeMoveProgress} -> {@code KnowledgeMoveProgressRespVo}。 */
    public static KnowledgeMoveProgressRespVo toVo(KnowledgeMoveProgress src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeMoveProgressRespVo(
                src.taskId(),
                src.sourceKbId(),
                src.targetKbId(),
                src.status(),
                src.progress(),
                src.total(),
                src.processed(),
                src.failed(),
                src.message(),
                src.error(),
                src.createdAt(),
                src.updatedAt());
    }

    /** {@code KnowledgeSearchResponse} -> {@code KnowledgeSearchRespVo}。 */
    public static KnowledgeSearchRespVo toVo(KnowledgeSearchResponse src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeSearchRespVo(
                toVoList(src.items(), KnowledgeAssembler::toVo),
                src.hasMore(),
                src.total());
    }

    /** {@code MoveKnowledgeResponse} -> {@code MoveKnowledgeRespVo}。 */
    public static MoveKnowledgeRespVo toVo(MoveKnowledgeResponse src) {
        if (src == null) {
            return null;
        }
        return new MoveKnowledgeRespVo(
                src.taskId(),
                src.sourceKbId(),
                src.targetKbId(),
                src.knowledgeCount());
    }

    /** {@code ReparseTaskData} -> {@code ReparseTaskDataRespVo}。 */
    public static ReparseTaskDataRespVo toVo(ReparseTaskData src) {
        if (src == null) {
            return null;
        }
        return new ReparseTaskDataRespVo(
                src.reparseCount(),
                src.taskId());
    }

    /** {@code KnowledgeResponse} -> {@code KnowledgeRespVo}。 */
    public static KnowledgeRespVo toVo(KnowledgeResponse src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeRespVo(
                src.id(),
                src.knowledgeBaseId(),
                src.tags(),
                src.type(),
                src.title(),
                src.description(),
                src.source(),
                src.channel(),
                src.parseStatus(),
                src.pendingSubtasksCount(),
                src.summaryStatus(),
                src.enableStatus(),
                src.embeddingModelId(),
                src.fileName(),
                src.folderPath(),
                src.fileType(),
                src.fileSize(),
                src.fileHash(),
                src.storageSize(),
                src.metadata(),
                src.customMetadata(),
                src.lastFaqImportResult(),
                src.createdAt(),
                src.updatedAt(),
                src.processedAt(),
                src.errorMessage(),
                src.knowledgeBaseName());
    }

    /** 列表装配，保持原有顺序。 */
    public static <S, T> List<T> toVoList(
            List<S> src, Function<S, T> mapper) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(mapper).toList();
    }

    /** 知识条目列表的分页响应。 */
    public static KnowledgeListRespVo toVo(KnowledgeListResponse src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeListRespVo(
                toVoList(src.items(), KnowledgeAssembler::toVo),
                src.page(), src.pageSize(), src.total());
    }

    /** 任务 id 响应。 */
    public static TaskIdRespVo toVo(TaskIdResponse src) {
        return src == null ? null : new TaskIdRespVo(src.taskId());
    }

    /** 清空结果响应。 */
    public static ClearContentsRespVo toVo(ClearContentsResponse src) {
        return src == null ? null : new ClearContentsRespVo(src.deletedCount());
    }

    /** {@code CopyKnowledgeBaseResponse} -> {@code CopyKnowledgeBaseRespVo}。 */
    public static CopyKnowledgeBaseRespVo toVo(CopyKnowledgeBaseResponse src) {
        if (src == null) {
            return null;
        }
        return new CopyKnowledgeBaseRespVo(
                src.taskId(),
                src.sourceId(),
                src.targetId());
    }

    /** {@code DuplicateKnowledgeBaseResponse} -> {@code DuplicateKnowledgeBaseRespVo}。 */
    public static DuplicateKnowledgeBaseRespVo toVo(DuplicateKnowledgeBaseResponse src) {
        if (src == null) {
            return null;
        }
        return new DuplicateKnowledgeBaseRespVo(
                src.sourceId(),
                src.targetId(),
                src.knowledgeBase());
    }

    /** {@code KBCloneProgress} -> {@code KbCloneProgressRespVo}。 */
    public static KbCloneProgressRespVo toVo(KBCloneProgress src) {
        if (src == null) {
            return null;
        }
        return new KbCloneProgressRespVo(
                src.taskId(),
                src.sourceId(),
                src.targetId(),
                src.status(),
                src.progress(),
                src.total(),
                src.processed(),
                src.message(),
                src.error(),
                src.createdAt(),
                src.updatedAt());
    }

    /** {@code RebuildIndexResponse} -> {@code RebuildIndexRespVo}。 */
    public static RebuildIndexRespVo toVo(RebuildIndexResponse src) {
        if (src == null) {
            return null;
        }
        return new RebuildIndexRespVo(
                src.documentCount());
    }

    /** {@code KnowledgeTagResponse} -> {@code KnowledgeTagRespVo}。 */
    public static KnowledgeTagRespVo toVo(KnowledgeTagResponse src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeTagRespVo(
                src.id(),
                src.seqId(),
                src.knowledgeBaseId(),
                src.name(),
                src.color(),
                src.sortOrder(),
                src.createdAt(),
                src.updatedAt());
    }

    /** {@code KnowledgeTagWithStats} -> {@code KnowledgeTagWithStatsRespVo}。 */
    public static KnowledgeTagWithStatsRespVo toVo(KnowledgeTagWithStats src) {
        if (src == null) {
            return null;
        }
        return new KnowledgeTagWithStatsRespVo(
                src.id(),
                src.seqId(),
                src.knowledgeBaseId(),
                src.name(),
                src.color(),
                src.sortOrder(),
                src.createdAt(),
                src.updatedAt(),
                src.knowledgeCount(),
                src.chunkCount());
    }
}
