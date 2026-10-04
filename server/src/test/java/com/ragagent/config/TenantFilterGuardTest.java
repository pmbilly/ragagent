package com.ragagent.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.ragagent.common.mybatis.TenantFilterGuard;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link TenantFilterGuard} 行为测试：探针 SQL 直接打拦截器。
 * alert 档用 Logback ListAppender 捕获告警；enforce 档以异常为红态。
 */
class TenantFilterGuardTest {

    private final TenantFilterGuard alert = new TenantFilterGuard(TenantFilterGuard.Mode.ALERT);
    private final TenantFilterGuard enforce = new TenantFilterGuard(TenantFilterGuard.Mode.ENFORCE);
    private final TenantFilterGuard off = new TenantFilterGuard(TenantFilterGuard.Mode.OFF);

    private ListAppender<ILoggingEvent> logs;
    private Logger guardLogger;

    @BeforeEach
    void attachLogs() {
        guardLogger = (Logger) LoggerFactory.getLogger(TenantFilterGuard.class);
        logs = new ListAppender<>();
        logs.start();
        guardLogger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        guardLogger.detachAppender(logs);
    }

    private static MappedStatement ms(String id, SqlCommandType type, String sql) {
        var cfg = new org.apache.ibatis.session.Configuration();
        return new MappedStatement.Builder(cfg, id, new StaticSqlSource(cfg, sql), type).build();
    }

    private void query(TenantFilterGuard guard, MappedStatement ms) {
        guard.beforeQuery(null, ms, Collections.emptyMap(),
                org.apache.ibatis.session.RowBounds.DEFAULT, null,
                new org.apache.ibatis.mapping.BoundSql(
                        new org.apache.ibatis.session.Configuration(),
                        ms.getBoundSql(Collections.emptyMap()).getSql(),
                        java.util.Collections.emptyList(), null));
    }

    private long warns() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    @Test
    @DisplayName("注册表无 tenant_id 谓词：alert 告警一次、enforce 抛异常、off 静默")
    void missingPredicateByMode() {
        var probe = ms("probe.sessions.noTenant", SqlCommandType.SELECT,
                "SELECT * FROM sessions ORDER BY updated_at DESC");

        query(alert, probe);
        assertThat(warns()).as("alert 档必须发出告警").isEqualTo(1);
        assertThat(logs.list.get(0).getFormattedMessage()).contains("sessions", "probe.sessions.noTenant");

        assertThatExceptionOfType(MybatisPlusException.class)
                .isThrownBy(() -> query(enforce, probe))
                .withMessageContaining("未带 tenant_id 谓词");

        query(off, probe);
        assertThat(warns()).as("off 档必须静默").isEqualTo(1);
    }

    @Test
    @DisplayName("带 tenant_id 谓词（含 t.tenant_id 限定名 / IS NULL 平台行）两档都放行")
    void predicatePresentPasses() {
        query(enforce, ms("probe.ok", SqlCommandType.SELECT,
                "SELECT * FROM sessions WHERE tenant_id = ? AND deleted_at IS NULL"));
        query(enforce, ms("probe.okQualified", SqlCommandType.SELECT,
                "SELECT s.* FROM sessions s WHERE s.tenant_id = ?"));
        query(enforce, ms("probe.okPlatform", SqlCommandType.SELECT,
                "SELECT * FROM skills WHERE tenant_id IS NULL"));
        assertThat(warns()).as("放行路径不应有告警").isZero();
    }

    @Test
    @DisplayName("未注册表（无租户列/自引用表）不检查")
    void unregisteredTableIgnored() {
        query(enforce, ms("probe.authTokens", SqlCommandType.SELECT,
                "SELECT * FROM auth_tokens"));
        query(enforce, ms("probe.tenants", SqlCommandType.SELECT,
                "SELECT * FROM tenants"));
        assertThat(warns()).isZero();
    }

    @Test
    @DisplayName("方言 SQL 解析失败 fail-open 放行（与 FullTableWriteGuard 同款）")
    void failsOpenOnDialectSql() {
        query(enforce, ms("probe.pgDialect", SqlCommandType.SELECT,
                "SELECT * FROM chunks WHERE metadata #- '{a}' IS NOT NULL"));
        assertThat(warns()).isZero();
    }

    @Test
    @DisplayName("v1 边界：JOIN 次表/UNION/FROM 子查询/非 SELECT 不查")
    void narrowBoundaries() {
        // JOIN：只查主表 chunks；sessions 是次表不拖累——但主表无谓词仍要红
        assertThatExceptionOfType(MybatisPlusException.class)
                .isThrownBy(() -> query(enforce, ms("probe.join", SqlCommandType.SELECT,
                        "SELECT c.* FROM chunks c JOIN knowledges k ON k.id = c.knowledge_id")));
        // UNION 复合查询跳过
        query(enforce, ms("probe.union", SqlCommandType.SELECT,
                "SELECT id FROM sessions UNION SELECT id FROM users"));
        // FROM 子查询跳过
        query(enforce, ms("probe.subqueryFrom", SqlCommandType.SELECT,
                "SELECT * FROM (SELECT id FROM sessions) t"));
        // 非 SELECT 不查
        query(enforce, ms("probe.update", SqlCommandType.UPDATE,
                "UPDATE sessions SET title = 'x'"));
        assertThat(warns()).isZero();
    }

    @Test
    @DisplayName("装配顺序：分页 → 全表防护 → 租户探测")
    void wiringOrder() {
        var chain = new MybatisPlusConfig().mybatisPlusInterceptor(
                TenantFilterGuard.Mode.ALERT).getInterceptors();
        assertThat(chain).hasSize(3);
        assertThat(chain.get(0)).isInstanceOf(com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor.class);
        assertThat(chain.get(1)).isInstanceOf(com.ragagent.common.mybatis.FullTableWriteGuard.class);
        assertThat(chain.get(2)).isInstanceOf(TenantFilterGuard.class);
    }
}
