/**
 * 跨域基础设施（被 30 个包依赖，位置最底）：统一响应与异常（{@code web/}、{@code error/}）、
 * 安全拦截（{@link com.ragagent.common.security.SsrfGuard}、{@code RbacInterceptor}）、JSON 工厂（{@link com.ragagent.common.web.JsonMappers}）、
 * 上下文/加密/文本/时间等无状态工具。**本包不得依赖任何业务域**。
 *
 * <p><b>契约层命名规范</b>（2026-10-08 B120 定，全表见
 * {@code docs/backend-package-map.md} §3.5）：端口方法用<b>领域类型名</b>做前缀
 * （{@code knowledgeBaseById}，不写 {@code kbById}）；单条 {@code …ById}、批量 {@code …ByIds}；
 * 不用无信息量的后缀（{@code …Unscoped} 而非 {@code …Only}）；仓储返回列表用 {@code list*}、
 * 服务返回实体用 {@code get*}；<b>同名方法跨接口必须同义</b>。</p>
 */
package com.ragagent.common;
