package com.ragagent.knowledge.service.query;

import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.dto.KnowledgeSearchScope;
import com.ragagent.knowledge.repository.KnowledgeBaseRepository;
import com.ragagent.knowledge.repository.KnowledgeRepository;
import com.ragagent.platform.access.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 知识检索。
 */
@Service
public class KnowledgeSearchService {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeBaseRepository kbRepository;

    public KnowledgeSearchService(KnowledgeRepository knowledgeRepository,
            KnowledgeBaseRepository kbRepository) {
        this.knowledgeRepository = knowledgeRepository;
        this.kbRepository = kbRepository;
    }

    /** 搜索结果。 */
    public record SearchOutcome(List<Knowledge> knowledges, boolean hasMore, long total) {}
    /** 检索作用域：租户 ID + 目标知识库 ID。 */

    // ── 搜索与移动/复制批：搜索与移动/复制（8 条路由的服务面） ──────────────────

    /**
     * <b>实现边界</b>：org-share（kbShareService）未实现——共享库的补捞分支恒空，
     * 与 ChunkAccessGuard/KnowledgeAccessGuard 的既有收紧同源；本租户文档库路径完整
     * （含 keyword LIKE 转义、file_types 别名、offset/limit+has_more、knowledge_base_name 回填）。
     */
    public SearchOutcome searchKnowledge(String keyword, int offset, int limit, List<String> fileTypes) {
        long tid = TenantContext.currentTenantId();
        List<KnowledgeSearchScope> scopes = new ArrayList<>();
        for (KnowledgeBase kb : kbRepository.listByTenant(tid)) {
            if ("document".equals(kb.getType())) {
                scopes.add(new KnowledgeSearchScope(tid, kb.getId()));
            }
        }
        return searchKnowledgeInScopes(scopes, keyword, offset, limit, fileTypes);
    }

    /**
     * JOIN knowledge_bases 限定 (tenant,kb) 对 +
     * {@code knowledge_bases.type='document'}，keyword 对 LOWER(file_name)/LOWER(title)
     * （xlsx↔xls / docx↔doc / jpg↔jpeg↔png，url/html → type='url'），
     * created_at DESC + limit+1 探测 has_more，total 是过滤后的全量计数。
     */
    public SearchOutcome searchKnowledgeInScopes(List<KnowledgeSearchScope> scopes, String keyword,
                                                 int offset, int limit, List<String> fileTypes) {
        if (scopes == null || scopes.isEmpty()) {
            return new SearchOutcome(null, false, 0);
        }
        // JOIN 语义：KB 行必须存在（同租户）且 type=document
        List<KnowledgeSearchScope> valid = new ArrayList<>();
        for (KnowledgeSearchScope s : scopes) {
            KnowledgeBase kb = kbRepository.findById(s.tenantId(), s.kbId());
            if (kb != null && "document".equals(kb.getType())) {
                valid.add(s);
            }
        }
        if (valid.isEmpty()) {
            return new SearchOutcome(null, false, 0);
        }
        KnowledgeRepository.SearchRows found =
                knowledgeRepository.searchScopedRows(valid, keyword, fileTypes, offset, limit);
        long total = found.total();
        List<Knowledge> rows = new ArrayList<>(found.rows());
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        // knowledge_base_name 回填
        Map<String, String> names = new HashMap<>();
        for (KnowledgeSearchScope s : valid) {
            KnowledgeBase kb = kbRepository.findById(s.tenantId(), s.kbId());
            if (kb != null) {
                names.put(kb.getId(), kb.getName());
            }
        }
        for (Knowledge row : rows) {
            row.setKnowledgeBaseName(names.getOrDefault(row.getKnowledgeBaseId(), ""));
        }
        return new SearchOutcome(rows, hasMore, total);
    }

    /** \、%、_ 前加反斜杠（LIKE 默认转义符，H2/PG 一致）。 */


    /**
     * 小写、去前导点、保序去重、别名互认
     * （xlsx↔xls / docx↔doc / jpg↔jpeg↔png）；url/html 折成 type='url' 条件（哨兵 &lt;&lt;url&gt;&gt;）。
     */


}
