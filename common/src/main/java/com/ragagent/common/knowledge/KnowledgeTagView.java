package com.ragagent.common.knowledge;

/**
 * 知识标签**不可变视图**（L1 载荷）：跨域消费者与
 * {@link KnowledgeCatalogPort} 的标签面读写都以此为口径。
 *
 * <p><b>为什么不是实体</b>：终态 `contracts` ＝「纯 JDK：公开 API / SPI / 不可变载荷」
 * （方案 §5.1）；持久化实体 `@TableName` 属各域内部（HANDOFF §12.0 判据 1）。
 * 字段取<b>消费者实际用度</b>——需要更多字段时先改本载荷，别把实体漏出去。</p>
 */
public record KnowledgeTagView(
        /** 标签 id。 */
        String id,
        /** 所属知识库 id。 */
        String knowledgeBaseId,
        /** 租户 id。 */
        Long tenantId,
        /** 标签名。 */
        String name,
        /** 颜色（可空）。 */
        String color,
        /** 排序号。 */
        Integer sortOrder) {
}
