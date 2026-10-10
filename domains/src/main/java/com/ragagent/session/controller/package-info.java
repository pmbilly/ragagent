/**
 * session 域控制器的 HTTP 端点与协作类（6 个协作类）。

 * <p><b>本包含控制器的「包私有协作类」</b>（{@code {@code QaAttachmentResolver} / {@code QaRequestBinder} / {@code QaRequestParser} / {@code QaSseOrchestrator} / {@code QaTurnExecutor} / {@code QaTurnFinalizer}}）：它们是**表现层的一部分**，不是放错位置。</p>
 *
 * <p><b>判据与证据</b>（2026-10-10 B178 全仓核过）：controller 包里的 25 个非控制器文件 —— 21 个直接依赖
 * Web/MVC 类型（{@code org.springframework.web} / {@code jakarta.servlet} / {@code com.ragagent.common.web}）；
 * 其余 4 个虽不依赖 Web 类型，但**引用控制器自身类型**（{@code KnowledgeQaController} / {@code QaRequestBinder} /
 * {@code SessionStreamController} / {@code WikiRequestSupport}）⇒ 搬到 service 会造出
 * {@code service → controller} 的**真倒挂** ✗，比原问题更糟。故一律**保持原位** ✓（它们全部是
 * {@code package-private}，封装本就在 ✓）。若将来要迁：先按此判据复核，并同步搬迁测试。</p>
 */
package com.ragagent.session.controller;
