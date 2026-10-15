package com.ragagent.common.wiki;

import java.util.List;

import com.ragagent.common.wiki.domain.WikiIndex;
import com.ragagent.contract.wiki.WikiPageIssueView;
import com.ragagent.contract.wiki.WikiPageNotFoundException;
import com.ragagent.contract.wiki.WikiLinkRepairResult;

/**
 * wiki 页读写端口（L1）：wiki 工具族消费的页面/issue/索引最小读写面。
 *
 * <p><b>为什么是端口</b>：会话域的 wiki 工具后端只做"页 CRUD、issue 登记、
 * 索引概览"，不持有 wiki 域的门面服务与编辑来源上下文；实现（含
 * editSource 归因、409 冲突等）在 wiki 域。实体已上提 L1，端口直接搬运。</p>
 *
 * <p><b>失败语义</b>与 wiki 域一致：页不存在 ⇒ {@link WikiPageNotFoundException}
 * （工具接缝把它转 null）；其余异常原样上抛。</p>
 */
public interface WikiPageOpsPort {

    /** 按 KB + slug 取页；不存在 ⇒ {@link WikiPageNotFoundException}。 */
    WikiPageView getPageBySlug(String kbId, String slug);

    /** 建页（editSource 为写归因来源，落库时读取）。 */
    WikiPageView createPage(WikiPageView page, String editSource);

    /** 更新页正文/字段（editSource 同上）。 */
    void updatePage(WikiPageView page, String editSource);

    /** 更新页内自动链接内容（editSource 同上）。 */
    void updateAutoLinkedContent(WikiPageView page, String editSource);

    /** 删页（软删；editSource 同上）。 */
    void deletePage(String kbId, String slug, String editSource);

    /** 修复正文链接；无可修复内容 ⇒ null（口径与域实现一致）。 */
    WikiLinkRepairResult repairContentLinks(String kbId, String selfSlug, String content);

    /** 往受影响页注入交叉链接。 */
    void injectCrossLinks(String kbId, List<String> affectedSlugs);

    /** 重建索引页。 */
    void rebuildIndexPage(String kbId);

    /** 列 issue（kbId/slug/status 均可空串 = 不过滤）。 */
    List<WikiPageIssueView> listIssues(String kbId, String slug, String status);

    /** 登记 issue。 */
    WikiPageIssueView createIssue(WikiPageIssueView issue);

    /** 更新 issue 状态。 */
    void updateIssueStatus(String issueId, String status);

    /** 关键字搜页。 */
    List<WikiPageView> searchPages(String kbId, String query, int limit);

    /** 索引概览（无类型过滤、全量统计口径）；无 overview ⇒ null。 */
    WikiIndex.Response getIndexView(String kbId, int topK);
}
