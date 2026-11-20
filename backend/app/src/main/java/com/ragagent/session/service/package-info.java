/**
 * 会话 · 编排核心：知识/Agent 两条问答服务、QA 上下文解析（{@code ChatManageFactory} /
 * {@code QaSupport}）、Agent 工具后端与编排协作件。
 *
 * <p>依赖 {@code contract}、{@code platform}、{@code modelcontext}、{@code session}；
 * 会话/消息生命周期在 {@code conversation}，附件在 {@code attachment}，
 * 管线注册在 {@code assembly}，HTTP 入口在 {@code controller}。</p>
 */
package com.ragagent.session.service;
