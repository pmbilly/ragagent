/**
 * 存储后端域：存储后端配置与凭据（对外 {@code /api/v1/storage-backends}）与文件服务
 * （传输、解析、文件解析器）。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code StorageBackendController}、{@code FileProxyController}<br>
 * <b>对外契约面</b>：{@code StorageBackendPort}、{@code ProviderFileStorePort}、{@code FileUrlPort}、
 * {@code GlobalFileServicePort}、{@code ResourceCatalogPort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.storage;
