/**
 * 仓储门面层：记忆域的持久化门面与其协作者。索引侧与写入侧同属一个关注点，故与
 * {@code MemoryRepository} 同包（协作者为包级私有，不做跨包暴露）。
 * <ul>
 *   <li>{@code MemoryRepository} — 记忆条目的读写门面；</li>
 *   <li>{@code MemoryIndexStore} / {@code MemoryItemStore} — 索引侧与条目侧存储；</li>
 *   <li>{@code MemoryVectorStore} — 向量召回（{@code VectorHitRow} 行载体在 mapper 包，
 *       因被 {@code MemoryItemEmbeddingMapper} 使用）；</li>
 *   <li>{@code MemoryTxTemplate} — 复合写的事务模板。</li>
 * </ul>
 */
package com.ragagent.memory.repository;
