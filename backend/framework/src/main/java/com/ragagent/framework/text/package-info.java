/**
 * 平台文本与标记卫生面（原 text + html 两包合并）：契约文本原语、繁简转换、
 * 请求语言解析、HTML→Markdown 转换、实体表与转义、路径/文件名/无效字符清洗、
 * 退化检测与码点序比较。
 *
 * <p>约束：只放纯 JDK 的无状态文本工具；消费方横跨契约面与全部引擎/域。</p>
 */
package com.ragagent.framework.text;
