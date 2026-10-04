package com.ragagent.common.mybatis;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 租户过滤缺失<b>探测</b>（租户过滤单点化评估的 Step 2，fail-loud 路线）。
 *
 * <p>背景：手工租户条件散落各仓储，漏写一处就是数据面越权（B60 技能读全表跨租户可见
 * 即实证）。与 TenantLineInnerInterceptor（SQL 级自动改写）相比，本类<b>不改写 SQL</b>，
 * 只检查「注册表的表在 SELECT 主查询里是否出现 {@code tenant_id} 谓词」，缺失按模式处置。
 * 这样避开了 TenantLine 的三个硬伤（见
 * {@code docs/persistence-tenant-filtering-evaluation.md}）：与 B60 的
 * {@code tenant_id IS NULL = 平台内置} 语义冲突、方言 SQL 解析失败即炸、ignore 表清单维护。</p>
 *
 * <p><b>三档模式</b>（{@code weknora.persistence.tenant-filter-guard}，默认 alert）：
 * {@code off} 关闭；{@code alert} 只告警（当前档——先盘出「直连租户过滤 vs 传递范围」
 * 的真实面，再决定登记白名单还是补条件）；{@code enforce} 抛
 * {@link MybatisPlusException}（白名单补齐后切这档收口）。</p>
 *
 * <p><b>v1 边界（刻意从窄，减少告警噪音）</b>：只查 SELECT 主 from-item 表（B60 型
 * 「读全表」是主攻面），JOIN 的次表与 UNION 分支不查；WHERE 判定按渲染串包含
 * {@code tenant_id}（子查询里出现也算过——宁漏勿误）；方言/动态 SQL 解析失败放行
 * （同 {@link FullTableWriteGuard} 的 fail-open）。这些边界收窄都登记在本注释里，
 * 收紧时改这里。</p>
 *
 * <p><b>表注册表</b>：由 migrations 推导（V1__baseline 带 {@code tenant_id} 的 46 表
 * + V4/V5 的 skills）。解析脚本输出直接粘贴，新增带租户列的表时同步维护。</p>
 */
public class TenantFilterGuard implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterGuard.class);

    /** 三档处置模式。 */
    public enum Mode { OFF, ALERT, ENFORCE }

    /**
     * 查询时必须出现 {@code tenant_id} 谓词的表（2026-10-05 由 V1__baseline.sql +
     * V5__skills_tenant.sql 推导；判定脚本是「CREATE TABLE 块内含 tenant_id 列」）。
     * 已知误报面：按父键传递范围的查询（如 chunks 按 knowledge_id）也会命中告警——
     * alert 档盘面后，属传递范围的表迁出本表、改在调用面注释范围语义。
     */
    public static final Set<String> TENANT_TABLES = Set.of(
            "audit_logs", "chunk_revisions", "chunks", "custom_agents", "data_sources",
            "embed_channels", "im_channel_sessions", "im_channels", "knowledge_bases",
            "knowledge_tags", "knowledges", "mcp_metadata", "mcp_oauth_clients",
            "mcp_oauth_tokens", "mcp_services", "mcp_tool_approvals", "memory_doc_affinity",
            "memory_extraction_sessions", "memory_item_embeddings", "memory_items",
            "memory_subjects", "memory_tombstones", "memory_topic_stats",
            "message_suggestion_events", "message_suggestion_sets", "models",
            "resource_bindings", "resources", "sessions", "skills", "storage_backends",
            "sync_logs", "task_dead_letters", "task_pending_ops", "temporary_documents",
            "tenant_api_keys", "tenant_invitations", "tenant_members", "user_kb_pins",
            "user_resource_favorites", "users", "vector_stores", "web_search_providers",
            "wiki_folders", "wiki_page_issues", "wiki_page_revisions", "wiki_pages");

    private final Mode mode;

    public TenantFilterGuard(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            org.apache.ibatis.session.RowBounds rowBounds,
                            org.apache.ibatis.session.ResultHandler resultHandler,
                            org.apache.ibatis.mapping.BoundSql boundSql) {
        if (mode == Mode.OFF || ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return;
        }
        String sql = boundSql.getSql();
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return; // fail-open，理由同 FullTableWriteGuard
        }
        if (!(statement instanceof Select select)
                || !(select.getSelectBody() instanceof PlainSelect plain)) {
            return; // UNION/复合查询 v1 不查（边界见类注释）
        }
        if (!(plain.getFromItem() instanceof Table table)) {
            return; // FROM 子查询/函数 v1 不查
        }
        String name = table.getName().toLowerCase();
        if (!TENANT_TABLES.contains(name)) {
            return;
        }
        String where = plain.getWhere() == null ? "" : plain.getWhere().toString().toLowerCase();
        if (where.contains("tenant_id")) {
            return; // 子查询里出现也算过（宁漏勿误，边界见类注释）
        }
        switch (mode) {
            case ENFORCE -> throw new MybatisPlusException(
                    "SELECT " + name + " 未带 tenant_id 谓词：" + ms.getId()
                            + "（传递范围则把表迁出 TenantFilterGuard.TENANT_TABLES 并注释范围语义；漏过滤则补条件）");
            default -> log.warn("[TenantFilterGuard] SELECT {} 未带 tenant_id 谓词：{}"
                            + "（alert 档只盘面不拦截；传递范围/白名单/补条件三选一，见类注释）",
                    name, ms.getId());
        }
    }
}
