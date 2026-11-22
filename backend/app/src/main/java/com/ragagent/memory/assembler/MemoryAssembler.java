package com.ragagent.memory.assembler;

import com.ragagent.framework.pagination.PageResult;
import com.ragagent.memory.controller.vo.MemoryConsolidationRespVo;
import com.ragagent.memory.controller.vo.MemoryDocRespVo;
import com.ragagent.memory.controller.vo.MemoryExportRespVo;
import com.ragagent.memory.controller.vo.MemoryItemRespVo;
import com.ragagent.memory.controller.vo.MemoryListRespVo;
import com.ragagent.memory.controller.vo.MemoryTopicRespVo;
import com.ragagent.memory.dto.MemoryConsolidationResult;
import com.ragagent.memory.dto.MemoryDocView;
import com.ragagent.memory.dto.MemoryExportResponse;
import com.ragagent.memory.dto.MemoryItemResponse;
import com.ragagent.memory.dto.MemoryTopicView;
import java.util.List;
import java.util.function.Function;

/** 记忆域对象到 HTTP 响应 VO 的装配。 */
public final class MemoryAssembler {

    private MemoryAssembler() {
    }

    /** 装配单个条目。 */
    public static MemoryItemRespVo toItemVo(MemoryItemResponse src) {
        if (src == null) {
            return null;
        }
        return new MemoryItemRespVo(
                src.id(),
                src.tenantId(),
                src.subjectId(),
                src.kind(),
                src.content(),
                src.topic(),
                src.normalizedKey(),
                src.importance(),
                src.origin(),
                src.status(),
                src.sourceSessionId(),
                src.sourceMessageId(),
                src.validFrom(),
                src.invalidAt(),
                src.expiresAt(),
                src.replacesId(),
                src.supersededBy(),
                src.lastUsedAt(),
                src.useCount(),
                src.createdAt(),
                src.updatedAt());
    }

    /** 批量装配条目，保持原有顺序。 */
    public static List<MemoryItemRespVo> toItemVoList(List<MemoryItemResponse> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(MemoryAssembler::toItemVo).toList();
    }

    /** 装配导出结果；空仓库的 {@code items} 保持 null，不兜底成空列表。 */
    public static MemoryExportRespVo toExportVo(MemoryExportResponse src) {
        if (src == null) {
            return null;
        }
        return new MemoryExportRespVo(
                toItemVoList(src.items()), src.total(), src.truncated());
    }

    /** 装配话题计数。 */
    public static MemoryTopicRespVo toTopicVo(MemoryTopicView src) {
        if (src == null) {
            return null;
        }
        return new MemoryTopicRespVo(
                src.getId(),
                src.getTopic(),
                src.getAliases(),
                src.getHits(),
                src.getThreshold(),
                src.getLastSeenAt());
    }

    /** 装配文档亲和度。 */
    public static MemoryDocRespVo toDocVo(MemoryDocView src) {
        if (src == null) {
            return null;
        }
        return new MemoryDocRespVo(
                src.getId(),
                src.getKnowledgeId(),
                src.getKnowledgeBaseId(),
                src.getTitle(),
                src.getHits(),
                src.getLastUsedAt());
    }

    /** 装配回顾结果。 */
    public static MemoryConsolidationRespVo toConsolidationVo(MemoryConsolidationResult src) {
        if (src == null) {
            return null;
        }
        return new MemoryConsolidationRespVo(
                src.getMerged(),
                src.getDemoted(),
                src.getExpired(),
                src.getReviewed(),
                src.getCandidates(),
                src.getSkipped());
    }

    /**
     * 装配分页壳：页号与页大小由 offset/limit 换算，元素逐项换形。
     *
     * <p>元素列表为 null 时保持 null（端点级空列表语义由 service 保证）。</p>
     */
    public static <S, V> MemoryListRespVo<V> toListVo(
            PageResult<S> page, Function<S, V> mapper, int limit, int offset) {
        List<S> items = page.items();
        List<V> voItems = items == null ? null : items.stream().map(mapper).toList();
        return new MemoryListRespVo<>(
                voItems, offset / limit + 1L, limit, page.total());
    }
}
