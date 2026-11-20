/**
 * wiki 摄取·链接图面（拆自 {@code wiki.service.ingest}，对齐方案 的 {@code service.ingest.graph/}）：
 * {@code WikiGraphCalculator}——从页面/引用计算链接图（入链、出链、孤儿）。
 *
 * <p>它对本域 service 面**零出边**（只读 {@code wiki.domain}），消费者是页面服务
 * （{@code wiki.service.page.DefaultWikiPageService}）与集成测试 ⇒ 单列为叶子面不成环。</p>
 */
package com.ragagent.wiki.service.ingest.graph;
