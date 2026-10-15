package com.ragagent.wiki.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.common.wiki.WikiLinkRepairResult;
import com.ragagent.common.wiki.WikiPageOpsPort;
import com.ragagent.common.wiki.domain.WikiIndex;
import com.ragagent.common.wiki.domain.WikiPage;
import com.ragagent.common.wiki.domain.WikiPageIssue;
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
    public List<WikiPageIssue> listIssues(String kbId, String slug, String status) {
        return wikiPageService.listIssues(kbId, slug, status);
    }

    @Override
    public WikiPageIssue createIssue(WikiPageIssue issue) {
        return wikiPageService.createIssue(issue);
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
}
