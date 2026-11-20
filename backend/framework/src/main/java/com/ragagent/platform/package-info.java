/**
 * 平台面（platform，阶段 6 起逐步收纳）：框架适配、可靠执行与通用技术能力。
 *
 * <p><b>边界</b>（方案 /）：这里放"技术底座"——事件总线、流与任务、HTTP/SSRF、
 * 持久化助手、文本与解析、存储与安全、追踪等；**不含业务状态机**，也不依赖任何业务域或
 * 三个 runtime 的实现。依赖方向：{@code platform → contracts}，跨模块协作经契约面。</p>
 *
 * <p>收纳进度：首个簇 {@code platform.stream} 已落位；{@code common.*} / {@code event.*} /
 * {@code tracing.*} 待按簇继续收拢。</p>
 */
package com.ragagent.platform;
