/**
 * 连接器契约面（方案 的 {@code domain.connector/}）：连接器 SPI（{@code Connector} /
 * {@code StreamingConnector}）、流式回调（{@code StreamHandler}）、目录与注册表
 * （{@code ConnectorCatalog} / {@code ConnectorRegistry} / {@code ConnectorMetadata}）、
 * HTTP 与异常（{@code ConnectorHttp} / {@code ConnectorHttpWiring} / {@code ConnectorException}）。
 *
 * <p><b>为什么 {@code StreamHandler} 在这里</b>：它是连接器的流式回调契约
 * （{@code emit}/{@code checkpoint}），被四个连接器实现 + {@code StreamingConnector} +
 * {@code service.DataSourceSyncExecutor} 使用——放 {@code task}（方案原列）会与
 * {@code task → Connector/ConnectorException} 组成 {@code task ⇄ domain.connector} 双环。
 * 本面只被 {@code task} 与 {@code service} 单向引用。</p>
 */
package com.ragagent.datasource.domain.connector;
