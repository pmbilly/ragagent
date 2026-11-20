package com.ragagent.knowledge.controller.vo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * FAQ 条目的 HTTP 响应体。
 *
 * <p>字段名与声明顺序即线格式：Jackson 按声明顺序输出，任何调序都会改变响应字节，
 * 因此这里的顺序必须与对外约定保持一致。</p>
 *
 * @param id                条目序号
 * @param chunkId           关联分块 id
 * @param knowledgeId       知识 id
 * @param knowledgeBaseId   知识库 id
 * @param tagId             标签序号，0 表示未打标签
 * @param tagName           标签名，未打标签时为 {@code 未分类}
 * @param enabled           是否启用
 * @param recommended       是否推荐
 * @param standardQuestion  标准问
 * @param similarQuestions  相似问列表
 * @param negativeQuestions 否定问列表
 * @param answers           答案列表
 * @param answerStrategy    答案策略
 * @param indexMode         索引模式
 * @param updatedAt         更新时间
 * @param createdAt         创建时间
 * @param chunkType         分块类型
 * @param match             检索命中信息，非检索场景为空
 */
public record FaqEntryRespVo(
        long id,
        String chunkId,
        String knowledgeId,
        String knowledgeBaseId,
        long tagId,
        String tagName,
        boolean enabled,
        boolean recommended,
        String standardQuestion,
        List<String> similarQuestions,
        List<String> negativeQuestions,
        List<String> answers,
        String answerStrategy,
        String indexMode,
        OffsetDateTime updatedAt,
        OffsetDateTime createdAt,
        String chunkType,
        FaqMatchVo match) {

    /**
     * 检索命中信息。
     *
     * @param score           相似度分数
     * @param type            命中方式（0 = 关键词，1 = 向量）
     * @param matchedQuestion 命中的问题原文
     */
    public record FaqMatchVo(double score, int type, String matchedQuestion) {
    }
}
