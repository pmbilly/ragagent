/**
 * 知识 · 入库状态：5 个 canonical 阶段（{@code IngestStage}）与一次尝试的内存态
 * （{@code IngestJob}）。
 *
 * <p>权威状态仍在 {@code knowledge.parse_status} 与 span 根行；本包只提供类型化的
 * 阶段名与链路内传递对象，不新增持久化字段。</p>
 */
package com.ragagent.knowledge.domain.ingest;
