/**
 * 模型配置域：模型条目 CRUD、凭据子资源与调试面（embedding 原始响应等）。
 * 模型运行时在 {@code model-runtime}；连通性测试与初始化在 {@code initialization}。
 *
 * <p><b>种类</b>：能力配置<br>
 * <b>入口</b>：{@code ModelController}、{@code ModelCredentialsController}、{@code ModelDebugController}<br>
 * <b>对外契约面</b>：{@code ModelCatalogPort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.model;
