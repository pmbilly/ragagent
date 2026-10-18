/**
 * 数据源连接器域：外部知识源（Notion/Feishu/Yuque/RSS/IMA/GitLab 等）的抓取、游标推进、
 * 同步编排与数据源配置面。连接器只负责“取回并转成仓内文档”，入库后的处理属 {@code knowledge}。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code DataSourceController}、{@code DataSourceCredentialsController}<br>
 * <b>对外契约面</b>：无（写入知识库走 {@code contract.knowledge} 端口）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.datasource;
