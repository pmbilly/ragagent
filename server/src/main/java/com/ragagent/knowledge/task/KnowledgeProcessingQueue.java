package com.ragagent.knowledge.task;

/**
 * 知识处理入队端口（原 {@code KnowledgeService.KnowledgeProcessWorker} 嵌套接口外提）。
 *
 * <p>生产方（门面、移动/解析/文件/批处理子服务、datasource 桥）只依赖本接口，
 * 实现方是 {@link KnowledgeProcessWorker}。端口独立成文件是 M2 解环的一部分：
 * 「提交任务」的类从此不在 bean 图上反向捏住 worker（R6 禁 {@code @Lazy} 的配套拆法）。</p>
 */
public interface KnowledgeProcessingQueue {

    /** 入队一个知识待处理任务（幂等语义由实现方保证）。 */
    void enqueue(String knowledgeId);
}
