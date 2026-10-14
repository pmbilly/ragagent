package com.ragagent.knowledge.domain.access;

import java.util.List;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;

/**
 * 访问守卫所需的**最小知识读取面**（B263③）。
 *
 * <p>原先三个守卫（{@code FaqGuard} / {@code KnowledgeAccessGuard} / {@code KnowledgeRouteGuards}）
 * 直接持有整个 {@code KnowledgeService}（796 行的枢纽）——这构成 knowledge SCC 的一条真实成环边
 * （{@code domain.access → service}）。改为依赖本端口后，守卫只看见 4 个读方法，依赖方向变为
 * **service 实现端口**（{@code service → domain.access}，本已存在），反向边消失 ⇒ 环断开。</p>
 *
 * <p>实现方：{@code com.ragagent.knowledge.service.management.KnowledgeService}（Spring 按类型注入，
 * 端口只有这一个实现）。若日后读取面归位到 {@code repository/}，实现随之替换即可。</p>
 */
public interface KnowledgeReadPort {

    /** 按 id 查 KB（软删不可见）。 */
    KnowledgeBase findKb(String kbId);

    /** 全局（不校验归属）取 Knowledge；找不到抛 {@code KnowledgeNotFoundException}。 */
    Knowledge getKnowledge(String id);

    /** 仅按 id 取 Knowledge（不做归属校验的原始读）。 */
    Knowledge getKnowledgeByIdOnly(String id);

    /** 批量取（保持入参顺序；租户内）。 */
    List<Knowledge> getKnowledgeBatch(long tenantId, List<String> ids);
}
