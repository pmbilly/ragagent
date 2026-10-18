/**
 * 仓储门面层：把 mapper 的原子操作组合成领域可用的读写契约，并集中承载软删、全字段
 * UPDATE 与 PG/H2 方言分支。
 * <ul>
 *   <li>{@code SessionRepository} — 会话行的读写契约（会话是本域聚合根）；</li>
 *   <li>{@code MessageRepository} — 消息读写契约，实现 {@code SessionMessagePort}
 *       （跨模块端口，供会话/记忆侧的检索与管线消费）；</li>
 *   <li>{@code MessageSuggestionRepository} — 追问建议的生成态与消费态；</li>
 *   <li>{@code TemporaryDocumentRepository} — 会话内临时文档（上传未入库的那份）。</li>
 * </ul>
 * <p>包内可见性是刻意的：这些门面与 mapper 包里的接口构成紧耦合对，不做跨包暴露。</p>
 */
package com.ragagent.session.repository;
