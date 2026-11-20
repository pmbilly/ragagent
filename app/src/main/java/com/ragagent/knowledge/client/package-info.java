/**
 * 知识库出站客户端：DocReaderClient（文档解析服务）。
 * 外部依赖的薄封装，只做协议转换与错误映射，不含业务规则。
 *
 * <p>向量化不再在此自建 HTTP 客户端——统一经 {@code ModelRuntime} / {@code EmbeddingPort}
 * （provider-aware、带响应校验）。</p>
 */
package com.ragagent.knowledge.client;
