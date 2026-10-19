/**
 * 解析引擎目录：本地静态注册表 + docreader 远端发现的合并（{@code ParserEngineCatalog}）。
 *
 * <p><b>种类</b>：产品能力（knowledge 域内的解析能力目录）<br>
 * <b>入口</b>：无 HTTP 面；由 system 的系统管理面经 {@code contract.doc.ParserEngineCatalogPort} 消费<br>
 * <b>对外契约面</b>：实现 {@code contract.doc.ParserEngineCatalogPort}<br>
 * <b>依赖</b>：{@code contract.doc}（DocReader 管理端口）。</p>
 */
package com.ragagent.knowledge.service.parser;
