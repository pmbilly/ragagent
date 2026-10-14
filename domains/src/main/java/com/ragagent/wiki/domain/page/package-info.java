/**
 * wiki 页面面（拆自 {@code wiki.domain}）：{@code WikiPage} 与页面修订、列表/移动/回滚/裁剪请求-响应、冲突与"不存在"异常，以及页面统计 {@code WikiStats}。
 *
 * <p><b>为什么 {@code WikiStats} 在这里</b>：它持有 {@code List<WikiPage>}（最近更新），留在 {@code wiki.domain} 会形成 {@code domain ⇄ domain.page} 双环；它本身也是"页面统计"。</p>
 */
package com.ragagent.wiki.domain.page;
