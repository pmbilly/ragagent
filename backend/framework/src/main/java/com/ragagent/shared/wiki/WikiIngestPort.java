package com.ragagent.shared.wiki;

/**
 * Wiki · 摄取入队端口（knowledge 入库收尾时触发）。
 *
 * <p><b>为什么是端口</b>：knowledge 处理完文档后要触发 wiki 摄取。此前 knowledge 直接注入
 * wiki 域的 {@code WikiIngestPort} 具体类，构成 {@code knowledge → wiki} 的实现类直连。</p>
 */
public interface WikiIngestPort {

    /**
     * 把文档的 wiki 摄取任务入队。
     *
     * @param tenantId        租户 id
     * @param knowledgeBaseId 知识库 id
     * @param knowledgeId     知识文档 id
     * @return 入队结果；{@code accepted=false} 时 {@code error} 说明原因
     */
    WikiEnqueueResult enqueueWikiIngest(long tenantId, String knowledgeBaseId, String knowledgeId);
}
