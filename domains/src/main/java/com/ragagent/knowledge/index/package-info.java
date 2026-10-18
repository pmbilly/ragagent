/**
 * 索引面：知识条目与向量/检索引擎的绑定与写入适配。
 *
 * <p>{@code KnowledgeVectorWrites} 提供“按 KB 解析已绑定引擎”的只读门面，
 * 由 {@code task/KnowledgeProcessWorker} 使用；引擎能力经 {@code contract.retrieval} 端口。</p>
 */
package com.ragagent.knowledge.index;
