/**
 * 联网搜索域：搜索 provider 目录与凭据（对外 {@code /api/v1/web-search-providers}）、
 * 搜索执行与各家 provider 适配。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code WebSearchController}、{@code WebSearchProviderController}<br>
 * <b>对外契约面</b>：{@code WebSearchProviderPort}、{@code WebSearchTempStatePort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.websearch;
