package com.ragagent.platform.persistence;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 软删过滤缺失探测：带 {@code deleted_at} 的表，SELECT 主查询必须出现该列谓词。
 *
 * <p>与 {@link TenantFilterGuard} 同一条路线：**不改写 SQL**，只探测。三档模式
 * （{@code weknora.persistence.soft-delete-guard}）：{@code off} / {@code alert} / {@code enforce}。
 * 默认 {@code enforce}——测试套件已全绿（存量查询均带谓词），确需读已删行的语句
 * 登记 {@link #ALLOWED_STATEMENTS}；降档排障用 alert/off。</p>
 *
 * <p><b>v1 边界</b>（刻意从窄）：只查 SELECT 主 from-item 表；JOIN 次表、UNION、
 * FROM 子查询不查；WHERE 渲染串包含 {@code deleted_at} 即过（子查询里出现也算，宁漏勿误）；
 * 方言/动态 SQL 解析失败放行。{@code deleted_at IS NOT NULL} 也算"有谓词"——
 * 回收站/恢复/去重等确需读已删行的语句，应登记 {@link #ALLOWED_STATEMENTS} 并写明族别，
 * 而不是靠 {@code IS NOT NULL} 绕过。</p>
 *
 * <p><b>表注册表</b>：由 {@code V1__baseline.sql} 推导（CREATE TABLE 块内含
 * {@code deleted_at} 列的 26 张表）后**逐表定性**，现 25 张——{@code knowledge_tags}
 * 虽有该列但从不写（硬删），已移出注册表；新增带软删列的表时同步维护。</p>
 */
public class SoftDeleteFilterGuard implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteFilterGuard.class);

    /** 三档处置模式。 */
    public enum Mode { OFF, ALERT, ENFORCE }

    /**
     * 带 {@code deleted_at} 列且**真按软删语义使用**的表（由基线迁移推导后逐表定性）。
     *
     * <p>{@code knowledge_tags} 不在此列：基线里虽有该列，但应用从不写它——
     * 标签删除是硬删（{@code DELETE FROM knowledge_tags}），软删谓词对它无意义。</p>
     */
    public static final Set<String> SOFT_DELETE_TABLES = Set.of(
            "chunks", "custom_agents", "data_sources", "embed_channels",
            "im_channel_sessions", "im_channels", "knowledge_bases", "knowledges",
            "mcp_services", "messages", "models", "resources", "sessions",
            "skills", "storage_backends", "temporary_documents", "tenant_invitations",
            "tenant_members", "tenants", "users", "vector_stores", "web_search_providers",
            "wiki_folders", "wiki_page_issues", "wiki_pages");

    /**
     * 确需读已删行的语句（回收站 / 恢复 / 审计 / 对账），逐条登记理由。
     *
     * <p>族别口径：
     * <ul>
     * <li><b>渠道来源判定面</b>——{@code /clear} 软删映射行后会话仍来自 IM，
     * 来源判定必须看到历史映射；</li>
     * <li><b>按 id 取行 + 调用方判定</b>——恢复/对账/管线任务需要看到已软删行
     * （如判定 {@code aborted} 后跳过），由调用方自行判 {@code deleted_at}；</li>
     * <li><b>唯一性/配额对账面</b>——软删行的旧值仍占用桶名、仍需按租户计额。</li>
     * </ul>
     * 只许减：新增条目必须能指出调用方如何判定已删行。</p>
     */
    public static final Set<String> ALLOWED_STATEMENTS = Set.of(
            // 渠道来源判定：/clear 软删映射行后会话仍来自 IM（SessionRepository.getImPlatform）
            "com.ragagent.session.mapper.SessionMapper.selectImPlatform",
            // 以下五条都是「按 id 取行、调用方判定」，对应各自的 findByIdAny / byIdIncludingDeleted
            "com.ragagent.auth.mapper.TenantMapper.selectById",              // 桶唯一性对账 + 配额读
            "com.ragagent.knowledge.mapper.ChunkMapper.selectById",          // 调用方自判 deleted_at
            "com.ragagent.knowledge.mapper.KnowledgeBaseMapper.selectById",  // 管线/凭据回填
            "com.ragagent.knowledge.mapper.KnowledgeMapper.selectById",      // 管线判 aborted 后跳过
            "com.ragagent.model.mapper.ModelMapper.selectById");             // 内置模型对账

    private final Mode mode;

    public SoftDeleteFilterGuard(Mode mode) {
        this.mode = mode;
    }

    /** 当前档位（测试/诊断用）。 */
    public Mode mode() {
        return mode;
    }

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
            return; // fail-open，理由同 TenantFilterGuard
        }
        if (!(statement instanceof PlainSelect plain)) {
            return; // UNION/复合查询 v1 不查
        }
        if (!(plain.getFromItem() instanceof Table table)) {
            return; // FROM 子查询/函数 v1 不查
        }
        String name = table.getName().toLowerCase();
        if (!SOFT_DELETE_TABLES.contains(name) || ALLOWED_STATEMENTS.contains(ms.getId())) {
            return;
        }
        String where = plain.getWhere() == null ? "" : plain.getWhere().toString().toLowerCase();
        if (where.contains("deleted_at")) {
            return;
        }
        switch (mode) {
            case ENFORCE -> throw new MybatisPlusException(
                    "SELECT " + name + " 未带 deleted_at 谓词：" + ms.getId()
                            + "（确需读已删行请在 SoftDeleteFilterGuard.ALLOWED_STATEMENTS 登记并注明族别；漏过滤则补条件）");
            default -> log.warn("[SoftDeleteFilterGuard] SELECT {} 未带 deleted_at 谓词：{}"
                            + "（alert 档只盘面；补条件或登记白名单）",
                    name, ms.getId());
        }
    }
}
