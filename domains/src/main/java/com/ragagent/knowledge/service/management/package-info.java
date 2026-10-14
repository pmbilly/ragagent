/**
 * 知识域**应用/门面面**（B263㊳ 由 {@code service} 迁入）：{@code KnowledgeService} 是本域对外的
 * 唯一门面（控制器与跨域消费方只认它），其余子服务（parse/summary/clone/move/search/span/batch…）
 * 留在 {@code service}，由门面向下调用。
 *
 * <p><b>为什么现在能搬</b>：门面在 {@code service} 包内的**代码级 in-degree 已归零**——原先的 6 个
 * "引用者"里 4 个只是 javadoc，唯一真边（{@code KnowledgeTagService} 的
 * {@code ObjectProvider<KnowledgeService>}）经 B263㊲ 证明是**残留的绕环手法**并已拆除
 * ⇒ 搬迁只产生 {@code service.management → service} 与 {@code service.{chunk,faq} → service.management}
 * 这些**单向边**，包级 SCC 不扩大。</p>
 */
package com.ragagent.knowledge.service.management;
