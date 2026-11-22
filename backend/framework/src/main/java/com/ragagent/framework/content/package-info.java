/**
 * 内容形态的通用能力：文档分块、FAQ 元数据编解码、图片引用改写、检索片段工具、检索作用域判定、语言解析。
 *
 * <p>这些是被 knowledge / session / agenttools 与检索引擎共用的文本与内容工具；
 * 它们既不能进任何单个域的 {@code api}（带 Jackson / ImageIO 的实现会污染契约面），
 * 也不能进域的 {@code service}/{@code support}（会破跨域只经契约的规则），因此留在 framework。</p>
 */
package com.ragagent.framework.content;
