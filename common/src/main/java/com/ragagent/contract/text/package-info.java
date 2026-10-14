/**
 * 契约文本工具面：契约载荷（wiki 标记/语言判定）共用的纯 JDK 文本原语。
 *
 * <p><b>有意偏离</b>：计划把 {@code Whitespace} 归 {@code platform.text}，但契约面载荷要用它 ⇒
 * 落契约面（纯 JDK，无框架耦合）；将来契约面不再需要时可迁 platform。</p>
 *
 */
package com.ragagent.contract.text;
