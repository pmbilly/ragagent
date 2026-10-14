/**
 * auth 域控制器的 HTTP 端点与协作类（7 个协作类）。

 * <p><b>本包含控制器的「包私有协作类」</b>（{@code {@code AuthBindingSupport} / {@code AuthOidcOps} / {@code AuthSessionOps} / {@code TenantBindSupport} / {@code TenantConfigOps} / {@code TenantCreateOps} / {@code TenantCrudOps}}）：它们是**表现层的一部分**，不是放错位置。</p>
 *
 * <p><b>判据与证据</b>（2026-10-10 全仓核过）：controller 包里的 25 个非控制器文件 —— 21 个直接依赖
 * Web/MVC 类型（{@code org.springframework.web} / {@code jakarta.servlet} / {@code com.ragagent.common.web}）；
 * 其余 4 个虽不依赖 Web 类型，但**引用控制器自身类型**（{@code KnowledgeQaController} / {@code QaRequestBinder} /
 * {@code SessionStreamController} / {@code WikiRequestSupport}）⇒ 搬到 service 会造出
 * {@code service → controller} 的**真倒挂**✗，比原问题更糟。故一律**保持原位**✓（它们全部是
 * {@code package-private}，封装本就在 ✓）。若将来要迁：先按此判据复核，并同步搬迁测试。</p>
 * <p>注：{@code QaRequestBinder} / {@code QaAttachmentResolver} 与 QA 编排类已随阶段 5
 * （2026-10-13）迁到 {@code com.ragagent.session.qa}；本段保留的是 2026-10-10 的核对记录。</p>
 */
package com.ragagent.auth.controller;
