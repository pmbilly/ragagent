/**
 * 仓储门面层：把 mapper 的原子操作组合成页面/文件夹级的读写契约，并集中承载软删、
 * 版本冲突与 PG 方言分支。
 * <ul>
 *   <li>{@code WikiPageRepository} — 页面读写契约（wiki 的聚合根，含版本与冲突处理）；</li>
 *   <li>{@code WikiFolderRepository} — 文件夹树与归属；</li>
 *   <li>{@code TaskPendingOpsRepository} — 待重放的写操作队列（摄取管线的"先落库、后执行"）；</li>
 *   <li>{@code TaskDeadLetterRepository} — 死信槽位（重试耗尽后的留存，供人工/运维重放）。</li>
 * </ul>
 */
package com.ragagent.wiki.repository;
