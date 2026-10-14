package com.ragagent.common.knowledge;

import java.util.List;

/**
 * 知识库目录端口：按租户列出**活动**知识库及其检索能力标志。
 *
 * <p>实现留在 {@code knowledge} 侧（{@code api/KnowledgeBaseCatalogAdapter}）——它委托
 * {@code KnowledgeBaseService.listKnowledgeBases(creator)}（含 {@code ensureDefaults} 的能力默认值回填；
 * <b>2026-09-25 线上抓回</b>：缺这一步会让 {@code capabilities} 全假 ⇒ 建议问题范围为空数组），
 * 再投影成本端口的记录。</p>
 *
 * <p><b>为什么要有它</b>：{@code agentmanagement} 属**适配面**，按 规则只允许依赖
 * contracts/platform 与各域**公开用例**——直连 {@code knowledge.service.*} 会绕过域边界
 * （本条债务即 基线的三处之一）。</p>
 */
public interface KnowledgeBaseCatalogPort {

    /** 单条目录项：id + 两个检索能力标志（默认值已由实现侧回填）。 */
    record KbCapability(String id, boolean vector, boolean keyword) {
    }

    /**
     * 当前租户的活动知识库（**排除隐藏库**，{@code created_at DESC}）。
     *
     * @param creator {@code null}/空 = 不过滤；{@code "mine"}/{@code "others"} = 按创建者过滤
     *                （与 {@code KnowledgeBaseService.listKnowledgeBases} 逐字同义）
     */
    List<KbCapability> listActiveCapabilities(String creator);
}
