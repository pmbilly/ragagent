package com.ragagent.wiki.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.contract.wiki.WikiLinkRepairResult;
import com.ragagent.common.wiki.WikiPageOpsPort;
import com.ragagent.common.wiki.domain.WikiIndex;
import com.ragagent.common.wiki.domain.WikiPage;
import com.ragagent.contract.wiki.WikiPageIssueView;
import com.ragagent.wiki.domain.WikiPageIssue;
import com.ragagent.wiki.service.page.WikiEditContext;
import com.ragagent.wiki.service.page.WikiPageService;

/**
 * {@link WikiPageOpsPort} 的 wiki 域实现：逐字转发 {@link WikiPageService}。
 *
 * <p><b>editSource 归因</b>是本适配器的实现细节：写面用
 * {@link WikiEditContext#callWith}/{@code runWith} 携带来源（原会话接缝的
 * 逐调用传递语义不变）；读面不经上下文。</p>
 */
@Component
public class WikiPageOpsAdapter implements WikiPageOpsPort {

    private final WikiPageService wikiPageService;

    public WikiPageOpsAdapter(WikiPageService wikiPageService) {
        this.wikiPageService = wikiPageService;
    }

    @Override
    public WikiPage getPageBySlug(String kbId, String slug) {
        return wikiPageService.getPageBySlug(kbId, slug);
    }

    @Override
    public WikiPage createPage(WikiPage page, String editSource) {
        return WikiEditContext.callWith(editSource, () -> wikiPageService.createPage(page));
    }

    @Override
    public void updatePage(WikiPage page, String editSource) {
        WikiEditContext.callWith(editSource, () -> wikiPageService.updatePage(page));
    }

    @Override
    public void updateAutoLinkedContent(WikiPage page, String editSource) {
        WikiEditContext.runWith(editSource,
                () -> wikiPageService.updateAutoLinkedContent(page));
    }

    @Override
    public void deletePage(String kbId, String slug, String editSource) {
        WikiEditContext.runWith(editSource, () -> wikiPageService.deletePage(kbId, slug));
    }

    @Override
    public WikiLinkRepairResult repairContentLinks(String kbId, String selfSlug,
            String content) {
        WikiPageService.RepairResult r =
                wikiPageService.repairContentLinks(kbId, selfSlug, content);
        return r == null ? null : new WikiLinkRepairResult(r.content(), r.changed());
    }

    @Override
    public void injectCrossLinks(String kbId, List<String> affectedSlugs) {
        wikiPageService.injectCrossLinks(kbId, affectedSlugs);
    }

    @Override
    public void rebuildIndexPage(String kbId) {
        wikiPageService.rebuildIndexPage(kbId);
    }

    @Override
    public List<WikiPageIssueView> listIssues(String kbId, String slug, String status) {
        return wikiPageService.listIssues(kbId, slug, status).stream()
                .map(WikiPageOpsAdapter::toIssueView).toList();
    }

    @Override
    public WikiPageIssueView createIssue(WikiPageIssueView issue) {
        WikiPageIssue saved = wikiPageService.createIssue(toIssueEntity(issue));
        // 旧语义：域服务就地把 id/createdAt 等写回**调用方那个对象** ⇒ 这里回填传入视图
        copyIssueInto(issue, saved);
        return toIssueView(saved);
    }

    @Override
    public void updateIssueStatus(String issueId, String status) {
        wikiPageService.updateIssueStatus(issueId, status);
    }

    @Override
    public List<WikiPage> searchPages(String kbId, String query, int limit) {
        return wikiPageService.searchPages(kbId, query, limit);
    }

    @Override
    public WikiIndex.Response getIndexView(String kbId, int topK) {
        // 索引视图：无类型过滤、无游标（全量统计口径）
        return wikiPageService.getIndexView(kbId, null, topK, "");
    }

    /** 实体 → 契约视图（逐字段搬运；null 透传）。 */
    static WikiPageIssueView toIssueView(WikiPageIssue e) {
        if (e == null) {
            return null;
        }
        WikiPageIssueView v = new WikiPageIssueView();
        v.setId(e.getId());
        v.setTenantId(e.getTenantId());
        v.setKnowledgeBaseId(e.getKnowledgeBaseId());
        v.setSlug(e.getSlug());
        v.setIssueType(e.getIssueType());
        v.setDescription(e.getDescription());
        v.setSuspectedKnowledgeIds(e.getSuspectedKnowledgeIds());
        v.setStatus(e.getStatus());
        v.setReportedBy(e.getReportedBy());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        v.setDeletedAt(e.getDeletedAt());
        return v;
    }

    /** 契约视图 → 实体（写面用；经实体 setter ⇒ 保留其空值归一语义；null 透传）。 */
    static WikiPageIssue toIssueEntity(WikiPageIssueView v) {
        if (v == null) {
            return null;
        }
        WikiPageIssue e = new WikiPageIssue();
        e.setId(v.getId());
        e.setTenantId(v.getTenantId());
        e.setKnowledgeBaseId(v.getKnowledgeBaseId());
        e.setSlug(v.getSlug());
        e.setIssueType(v.getIssueType());
        e.setDescription(v.getDescription());
        e.setSuspectedKnowledgeIds(v.getSuspectedKnowledgeIds());
        e.setStatus(v.getStatus());
        e.setReportedBy(v.getReportedBy());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        e.setDeletedAt(v.getDeletedAt());
        return e;
    }

    /** 把落库后的实体字段回填到传入视图（保持"同对象被填充"的旧语义；null 安全）。 */
    static void copyIssueInto(WikiPageIssueView v, WikiPageIssue e) {
        if (v == null || e == null) {
            return;
        }
        WikiPageIssueView fresh = toIssueView(e);
        v.setId(fresh.getId());
        v.setTenantId(fresh.getTenantId());
        v.setKnowledgeBaseId(fresh.getKnowledgeBaseId());
        v.setSlug(fresh.getSlug());
        v.setIssueType(fresh.getIssueType());
        v.setDescription(fresh.getDescription());
        v.setSuspectedKnowledgeIds(fresh.getSuspectedKnowledgeIds());
        v.setStatus(fresh.getStatus());
        v.setReportedBy(fresh.getReportedBy());
        v.setCreatedAt(fresh.getCreatedAt());
        v.setUpdatedAt(fresh.getUpdatedAt());
        v.setDeletedAt(fresh.getDeletedAt());
    }
}
