/**
 * 平台 · 知识/文本/图片工具：分块文本检索、文档分块组装、FAQ 元数据编解码、图片引用富化与重写。
 *
 * <p><b>为什么在 platform（而不是 knowledge 域）</b>：这批工具被多个业务族共享
 * （knowledge / session / wiki / agenttools），且多数依赖 Jackson 或 ImageIO——
 * 既不能进纯 JDK 的 {@code contract}，也不能归单一域（会制造跨族直连）。
 * 它们是"对契约形状做技术性组装"的适配工具，不是业务规则。</p>
 *
 * <p>判定一个文件该不该留下的三条检查：① 是否被 ≥2 个业务族消费；
 * ② 是否依赖框架/IO（Jackson / ImageIO）；③ 搬走是否只是换个位置而不减少依赖。
 * 任一成立即留在这里，并在 {@code platform-shapes.baseline.json} 登记理由。</p>
 */
package com.ragagent.platform.knowledge;
