/**
 * 知识侧启动恢复：重启后把卡在 pending/processing/finalizing/deleting 的解析行复位为
 * failed 并取消孤儿 span，summary 的 pending/processing 同样复位。
 *
 * <p>SQL 与状态机语义属于 knowledge 域；boot 只负责启动触发与编排
 * （{@code StartupTaskRecovery}>{@link com.ragagent.knowledge.recovery.KnowledgeStartupRecovery}）。</p>
 */
package com.ragagent.knowledge.recovery;
