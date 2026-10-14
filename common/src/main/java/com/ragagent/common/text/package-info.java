/**
 * L1 底座文本工具（**暂留 `common`**，非 `platform.text`）：`Whitespace` / `TextConv` 被
 * 契约面（`common.wiki.WikiLanguageSupport` 等）与 platform 面**共用** ⇒ 放 platform 会造
 * `common ⇄ platform` 顶层环 ✗（B268② 实测）。待「契约面去框架化」批把契约侧依赖清掉后再迁。
 */
package com.ragagent.common.text;
