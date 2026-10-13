/**
 * session 域控制器的 HTTP 端点（协议适配层）。
 *
 * <p><b>阶段 5 起：编排不在这一包。</b>QA 轮的解析、SSE 编排、整轮执行与收尾已迁到
 * {@code com.ragagent.session.qa}（{@code QaRequestParser} / {@code QaSseOrchestrator} /
 * {@code QaTurnExecutor} / {@code QaTurnFinalizer} + 协作件 {@code QaRequestBinder} /
 * {@code QaAttachmentResolver}）；本包只留路由与请求/响应壳（
 * {@code *Controller} / {@code *Request} / {@code *View}…）。</p>
 *
 * <p><b>与 2026-10-10 那次核对的差异（如实记录）</b>：当时判据是"协作类若引用控制器自身类型，
 * 搬走会造出 {@code service → controller} 的真倒挂，故保持原位"。本批换了做法——
 * 把控制器里被编排引用的**静态助手与内嵌类型**（{@code stringListOf} / {@code maxFileBytes} /
 * {@code appendQuickAnswerReasoning} / {@code ParsedRequest} / {@code Base64Support} /
 * {@code sanitizeForLog}）一并提出来（{@code session.qa.QaTurnSupport} /
 * {@code session.qa.ParsedRequest} / {@code session.qa.Base64Support} /
 * {@code session.support.LogSanitizer}），于是搬迁不再产生倒挂：依赖方向是
 * {@code controller → qa / support}，包里没有任何 {@code qa → controller} 引用。</p>
 */
package com.ragagent.session.controller;
