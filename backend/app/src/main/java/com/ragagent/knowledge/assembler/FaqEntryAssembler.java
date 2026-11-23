package com.ragagent.knowledge.assembler;

import com.ragagent.framework.pagination.PageResult;
import com.ragagent.knowledge.controller.vo.FaqEntryRespVo;
import com.ragagent.knowledge.dto.faq.FaqEntry;
import java.util.List;

/** 领域对象到 FAQ 响应 VO 的装配。 */
public final class FaqEntryAssembler {

    private FaqEntryAssembler() {
    }

    /**
     * 装配条目响应。
     *
     * <p>命中信息（{@code match}）只在检索结果里出现，普通查询为空。</p>
     */
    public static FaqEntryRespVo toVo(FaqEntry entry) {
        if (entry == null) {
            return null;
        }
        FaqEntry.FaqMatch match = entry.match();
        return new FaqEntryRespVo(
                String.valueOf(entry.id()),
                entry.chunkId(),
                entry.knowledgeId(),
                entry.knowledgeBaseId(),
                entry.tagId(),
                entry.tagName(),
                entry.enabled(),
                entry.recommended(),
                entry.standardQuestion(),
                entry.similarQuestions(),
                entry.negativeQuestions(),
                entry.answers(),
                entry.answerStrategy(),
                entry.indexMode(),
                entry.updatedAt(),
                entry.createdAt(),
                entry.chunkType(),
                match == null
                        ? null
                        : new FaqEntryRespVo.FaqMatchVo(
                                match.score(), match.type(), match.matchedQuestion()));
    }

    /** 批量装配，保持原有顺序。 */
    public static List<FaqEntryRespVo> toVoList(List<FaqEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().map(FaqEntryAssembler::toVo).toList();
    }

    /** 分页装配：页号与页大小原样透传，只换元素形状。 */
    public static PageResult<FaqEntryRespVo> toVoPage(PageResult<FaqEntry> page) {
        if (page == null) {
            return null;
        }
        return PageResult.of(
                toVoList(page.items()), page.total(), page.page(), page.pageSize());
    }
}
