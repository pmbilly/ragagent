/**
 * L1 底座 JSON 助手（**暂留 `common`**，非 `platform.web`）：`JsonMappers`（共享 ObjectMapper 工厂）
 * 与 `SortedMapSerializer` 被契约面（`common.knowledge.FaqChunkMetadata` /
 * `common.retrieval.SearchResult`）与 platform 面共用 ⇒ 放 platform 会造 `common ⇄ platform` 环 ✗。
 * 它们的去除路径 = 契约面去 Jackson（与 B258 登记的 `AppError` 同族）。
 */
package com.ragagent.common.web;
