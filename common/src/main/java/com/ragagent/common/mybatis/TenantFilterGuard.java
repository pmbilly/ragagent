package com.ragagent.common.mybatis;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
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
 * <p><b>三档模式</b>（{@code weknora.persistence.tenant-filter-guard}，默认 enforce）：
 * {@code off} 关闭；{@code alert} 只告警（盘面用）；{@code enforce} 抛
 * {@link MybatisPlusException}——B71 已完成逐表定性（72 条存量语句归入
 * {@link #ALLOWED_STATEMENTS}），默认档自 2026-10-05 起为 enforce；未登记的新增
 * 无租户谓词查询会在执行期直接红。</p>
 *
 * <p><b>v1 边界（刻意从窄，减少告警噪音）</b>：只查 SELECT 主 from-item 表（B60 型
 * 「读全表」是主攻面），JOIN 的次表与 UNION 分支不查；WHERE 判定按渲染串包含
 * {@code tenant_id}（子查询里出现也算过——宁漏勿误）；方言/动态 SQL 解析失败放行
 * （同 {@link FullTableWriteGuard} 的 fail-open）。这些边界收窄都登记在本注释里，
 * 收紧时改这里。</p>
 *
 * <p><b>表注册表</b>：由 migrations 推导（{@code V1__baseline.sql}：带 {@code tenant_id} 的 46 表
 * + {@code skills}；B156 起 V4/V5 已折叠进 V1）。解析脚本输出直接粘贴，新增带租户列的表时同步维护。</p>
 */
public class TenantFilterGuard implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterGuard.class);

    /** 三档处置模式。 */
    public enum Mode { OFF, ALERT, ENFORCE }

    /**
     * 查询时必须出现 {@code tenant_id} 谓词的表（2026-10-05 由 V1__baseline.sql +
     * V5__skills_tenant.sql 推导；B156 起 V5 已折叠 ⇒ **只看 V1__baseline.sql 即可**，且 skills 的
     * {@code tenant_id} 已直接写在 CREATE 里）。判定脚本是「CREATE TABLE 块内含 tenant_id 列」。
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

    /**
     * 语句级白名单（B71 逐表定性，2026-10-05；首次盘面 72 条语句全部归类）。
     * 增删条目必须注明族别。族别口径：
     * <ul>
     *   <li><b>认证面</b>——按 hash/bot 身份/邮箱定位，请求期不存在租户上下文；</li>
     *   <li><b>调度面</b>——后台轮询全表（IM 渠道投递、数据源同步），天然跨租户；</li>
     *   <li><b>跨空间身份关系面</b>——成员/邀请/收藏按 user 维度，天生跨空间；</li>
     *   <li><b>按 id/父键传递</b>——UUID 取行 + 上层守卫（requireKb / getKnowledgeInTenant /
     *       ChunkAccessGuard）校验归属；或按父键（kb_id / knowledge_id / item_id）传递范围；</li>
     *   <li><b>内部任务队列</b>——(task_type, scope, scope_id) 三元组即租户边界。</li>
     * </ul>
     */
    public static final Set<String> ALLOWED_STATEMENTS = Set.of(
            // ── 认证面 ──
            "com.ragagent.channels.api.mapper.TenantAPIKeyMapper.listByPlaceholderHash",
            "com.ragagent.channels.api.mapper.TenantAPIKeyMapper.listPlatform",
            "com.ragagent.channels.api.mapper.TenantAPIKeyMapper.selectByHash",
            "com.ragagent.channels.api.mapper.TenantAPIKeyMapper.selectFirstPlaceholderHashId",
            "com.ragagent.auth.mapper.UserMapper.selectById",
            "com.ragagent.auth.mapper.UserMapper.selectCount",
            "com.ragagent.auth.mapper.UserMapper.selectList",
            "com.ragagent.im.mapper.ImChannelMapper.findByBotIdentity",
            // ── 调度面 ──
            "com.ragagent.im.mapper.ImChannelMapper.listEnabled",
            "com.ragagent.datasource.mapper.DataSourceMapper.selectActive",
            "com.ragagent.datasource.mapper.SyncLogMapper.countByStatus",
            "com.ragagent.datasource.mapper.SyncLogMapper.selectLatest",
            // ── 跨空间身份关系面 ──
            "com.ragagent.auth.mapper.TenantMemberMapper.selectList",
            "com.ragagent.auth.mapper.TenantInvitationMapper.selectCount",
            "com.ragagent.auth.mapper.TenantInvitationMapper.selectList",
            "com.ragagent.knowledge.mapper.UserKbPinMapper.selectList",
            // ── 按 id/父键传递（上层守卫校验归属）──
            "com.ragagent.knowledge.mapper.ChunkMapper.selectById",
            "com.ragagent.knowledge.mapper.ChunkMapper.selectCount",
            "com.ragagent.knowledge.mapper.ChunkMapper.selectList",
            "com.ragagent.knowledge.mapper.KnowledgeMapper.selectById",
            "com.ragagent.knowledge.mapper.KnowledgeMapper.selectCount",
            "com.ragagent.knowledge.mapper.KnowledgeMapper.selectList",
            "com.ragagent.knowledge.mapper.KnowledgeBaseMapper.selectById",
            "com.ragagent.knowledge.mapper.KnowledgeBaseMapper.selectList",
            "com.ragagent.knowledge.mapper.KnowledgeTagMapper.maxSeqId",
            "com.ragagent.agent.management.mapper.AgentQuestionMapper.findKbs",
            "com.ragagent.memory.mapper.MemoryItemEmbeddingMapper.selectByItemId",
            "com.ragagent.session.mapper.SessionMapper.selectList",
            "com.ragagent.datasource.mapper.DataSourceMapper.selectByIdOrNull",
            "com.ragagent.datasource.mapper.DataSourceMapper.selectByKnowledgeBase",
            "com.ragagent.datasource.mapper.SyncLogMapper.selectByDataSource",
            "com.ragagent.datasource.mapper.SyncLogMapper.selectByIdOrNull",
            "com.ragagent.im.mapper.ImChannelMapper.getById",
            "com.ragagent.embedchannel.mapper.EmbedChannelMapper.getById",
            "com.ragagent.wiki.mapper.WikiPageMapper.countByType",
            "com.ragagent.wiki.mapper.WikiPageMapper.countByTypeLight",
            "com.ragagent.wiki.mapper.WikiPageMapper.countList",
            "com.ragagent.wiki.mapper.WikiPageMapper.countLiveById",
            "com.ragagent.wiki.mapper.WikiPageMapper.countOrphans",
            "com.ragagent.wiki.mapper.WikiPageMapper.countPagesByFolder",
            "com.ragagent.wiki.mapper.WikiPageMapper.countPagesInFolder",
            "com.ragagent.wiki.mapper.WikiPageMapper.findPagesByNormalizedTitles",
            "com.ragagent.wiki.mapper.WikiPageMapper.list",
            "com.ragagent.wiki.mapper.WikiPageMapper.listAll",
            "com.ragagent.wiki.mapper.WikiPageMapper.listBySourceRef",
            "com.ragagent.wiki.mapper.WikiPageMapper.listByType",
            "com.ragagent.wiki.mapper.WikiPageMapper.listByTypeLight",
            "com.ragagent.wiki.mapper.WikiPageMapper.listByTypeRecent",
            "com.ragagent.wiki.mapper.WikiPageMapper.listLiteBySlugs",
            "com.ragagent.wiki.mapper.WikiPageMapper.listPagesByFolderIds",
            "com.ragagent.wiki.mapper.WikiPageMapper.listPagesCursor",
            "com.ragagent.wiki.mapper.WikiPageMapper.listSlugsBySourceRef",
            "com.ragagent.wiki.mapper.WikiPageMapper.listSummariesByKnowledgeIDs",
            "com.ragagent.wiki.mapper.WikiPageMapper.search",
            "com.ragagent.wiki.mapper.WikiPageMapper.selectAllLiveSlugs",
            "com.ragagent.wiki.mapper.WikiPageMapper.selectLiveBySlug",
            "com.ragagent.wiki.mapper.WikiPageMapper.selectLiveSlugs",
            "com.ragagent.wiki.mapper.WikiFolderMapper.countLiveById",
            "com.ragagent.wiki.mapper.WikiFolderMapper.listAllFolders",
            "com.ragagent.wiki.mapper.WikiFolderMapper.listChildFolders",
            "com.ragagent.wiki.mapper.WikiFolderMapper.listDistinctPaths",
            "com.ragagent.wiki.mapper.WikiFolderMapper.selectChildByName",
            "com.ragagent.wiki.mapper.WikiFolderMapper.selectFolderById",
            "com.ragagent.wiki.mapper.WikiPageRevisionMapper.countRevisions",
            "com.ragagent.wiki.mapper.WikiPageRevisionMapper.listRevisions",
            "com.ragagent.wiki.mapper.WikiPageRevisionMapper.selectRevision",
            "com.ragagent.wiki.mapper.WikiPageIssueMapper.listIssues",
            // ── 内部任务队列（scope 三元组即租户边界）──
            "com.ragagent.wiki.mapper.TaskPendingOpMapper.distinctIngestScopeIds",
            "com.ragagent.wiki.mapper.TaskPendingOpMapper.selectById",
            "com.ragagent.wiki.mapper.TaskPendingOpMapper.selectCount",
            "com.ragagent.wiki.mapper.TaskPendingOpMapper.selectList",
            "com.ragagent.wiki.mapper.TaskDeadLetterMapper.selectList");

    /** raw {@code ResultHandler} 是父接口签名：参数化会因擦除相同而破坏覆盖（name clash），
     *  MyBatis 侧未提供通配签名，故此处只能按 raw 覆写并就地抑制。 */
    @Override
    @SuppressWarnings("rawtypes")
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
        // JSqlParser 4.7+：普通 SELECT 的 parse 结果即 PlainSelect 本身
        // （旧的 Select.getSelectBody() 已废弃）；UNION/SetOperation 不满足即不查
        if (!(statement instanceof PlainSelect plain)) {
            return; // UNION/复合查询 v1 不查（边界见类注释）
        }
        if (!(plain.getFromItem() instanceof Table table)) {
            return; // FROM 子查询/函数 v1 不查
        }
        String name = table.getName().toLowerCase();
        if (!TENANT_TABLES.contains(name)) {
            return;
        }
        if (ALLOWED_STATEMENTS.contains(ms.getId())) {
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
