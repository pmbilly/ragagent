package com.ragagent.knowledge.assembler;

import com.ragagent.knowledge.controller.vo.*;
import com.ragagent.knowledge.dto.chunk.*;
import com.ragagent.knowledge.dto.faq.GeneratedQuestionResponse;
import com.ragagent.shared.pagination.PageResult;
import java.util.List;
import java.util.function.Function;

/** 知识域分片相关对象的响应装配：只做字段搬运与空值处理。 */
public final class ChunkAssembler {

    private ChunkAssembler() {
    }

    /** ChunkResponse -> ChunkRespVo。 */
    public static ChunkRespVo toVo(ChunkResponse src) {
        if (src == null) {
            return null;
        }
        return new ChunkRespVo(
                src.id(),
                src.seqId(),
                src.knowledgeId(),
                src.knowledgeBaseId(),
                src.tagId(),
                src.content(),
                src.contentRevision(),
                src.indexStatus(),
                src.lastEditorId(),
                src.chunkIndex(),
                src.enabled(),
                src.flags(),
                src.status(),
                src.startAt(),
                src.endAt(),
                src.preChunkId(),
                src.nextChunkId(),
                src.chunkType(),
                src.parentChunkId(),
                src.relationChunks(),
                src.indirectRelationChunks(),
                src.metadata(),
                src.contentHash(),
                src.imageInfo(),
                src.createdAt(),
                src.updatedAt());
    }

    /** ChunkRevisionResponse -> ChunkRevisionRespVo。 */
    public static ChunkRevisionRespVo toVo(ChunkRevisionResponse src) {
        if (src == null) {
            return null;
        }
        return new ChunkRevisionRespVo(
                src.id(),
                src.knowledgeBaseId(),
                src.knowledgeId(),
                src.chunkId(),
                src.revision(),
                src.content(),
                src.enabled(),
                src.editorId(),
                src.editSource(),
                src.editedAt(),
                src.createdAt());
    }

    /** ChunkUpdateResponse -> ChunkUpdateRespVo。 */
    public static ChunkUpdateRespVo toVo(ChunkUpdateResponse src) {
        if (src == null) {
            return null;
        }
        return new ChunkUpdateRespVo(
                toVo(src.chunk()),
                src.description(),
                src.summaryStatus());
    }

    /** GeneratedQuestionResponse -> GeneratedQuestionRespVo。 */
    public static GeneratedQuestionRespVo toVo(GeneratedQuestionResponse src) {
        if (src == null) {
            return null;
        }
        return new GeneratedQuestionRespVo(
                src.id(),
                src.question(),
                src.contentRevision());
    }

    /** 批量装配，保持原有顺序。 */
    public static <S, T> List<T> toVoList(List<S> src, Function<S, T> mapper) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(mapper).toList();
    }

    /** 分页装配：页号与页大小原样透传。 */
    public static <S, T> PageResult<T> toVoPage(PageResult<S> page,
            Function<S, T> mapper) {
        if (page == null) {
            return null;
        }
        return PageResult.of(
                toVoList(page.items(), mapper), page.total(), page.page(), page.pageSize());
    }
}
