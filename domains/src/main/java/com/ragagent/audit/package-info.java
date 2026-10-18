/**
 * 审计日志域：审计条目的写入、查询与保留期清理。审计行只追加不修改。
 *
 * <p><b>种类</b>：控制面<br>
 * <b>入口</b>：{@code AuditLogController}<br>
 * <b>对外契约面</b>：{@code AuditWritePort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.audit;
