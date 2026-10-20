package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.ragagent.platform.persistence.SoftDeleteFilterGuard;
import java.util.Collections;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link SoftDeleteFilterGuard} 行为测试：探针 SQL 直接打拦截器。
 * alert 档用 Logback ListAppender 捕获告警；enforce 档以异常为红态。
 */
class SoftDeleteFilterGuardTest {

    private final SoftDeleteFilterGuard alert = new SoftDeleteFilterGuard(SoftDeleteFilterGuard.Mode.ALERT);
    private final SoftDeleteFilterGuard enforce = new SoftDeleteFilterGuard(SoftDeleteFilterGuard.Mode.ENFORCE);
    private final SoftDeleteFilterGuard off = new SoftDeleteFilterGuard(SoftDeleteFilterGuard.Mode.OFF);

    private ListAppender<ILoggingEvent> logs;
    private Logger guardLogger;

    @BeforeEach
    void attachLogs() {
        guardLogger = (Logger) LoggerFactory.getLogger(SoftDeleteFilterGuard.class);
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

    private void query(SoftDeleteFilterGuard guard, MappedStatement ms) {
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
    @DisplayName("带 deleted_at 的表缺谓词：alert 告警一次、enforce 抛异常、off 静默")
    void missingPredicateByMode() {
        var probe = ms("probe.chunks.noDeletedAt", SqlCommandType.SELECT,
                "SELECT * FROM chunks WHERE tenant_id = ? ORDER BY seq_id DESC");

        query(alert, probe);
        assertThat(warns()).as("alert 档必须发出告警").isEqualTo(1);
        assertThat(logs.list.get(0).getFormattedMessage()).contains("chunks", "probe.chunks.noDeletedAt");

        assertThatExceptionOfType(MybatisPlusException.class)
                .isThrownBy(() -> query(enforce, probe))
                .withMessageContaining("未带 deleted_at 谓词");

        query(off, probe);
        assertThat(warns()).as("off 档必须静默").isEqualTo(1);
    }

    @Test
    @DisplayName("deleted_at IS NULL / IS NOT NULL 都算有谓词")
    void predicatePresentPasses() {
        query(enforce, ms("probe.live", SqlCommandType.SELECT,
                "SELECT * FROM chunks WHERE tenant_id = ? AND deleted_at IS NULL"));
        query(enforce, ms("probe.deleted", SqlCommandType.SELECT,
                "SELECT * FROM chunks WHERE tenant_id = ? AND deleted_at IS NOT NULL"));
    }

    @Test
    @DisplayName("未登记软删列的表不查")
    void unregisteredTablePasses() {
        query(enforce, ms("probe.audit", SqlCommandType.SELECT,
                "SELECT * FROM audit_logs WHERE tenant_id = ?"));
    }

    @Test
    @DisplayName("非 SELECT / 解析失败 / UNION 一律放行（v1 边界）")
    void boundariesFailOpen() {
        query(enforce, ms("probe.update", SqlCommandType.UPDATE,
                "UPDATE chunks SET content = ? WHERE id = ?"));
        query(enforce, ms("probe.union", SqlCommandType.SELECT,
                "SELECT id FROM chunks WHERE tenant_id = ? UNION SELECT id FROM knowledges"));
        query(enforce, ms("probe.broken", SqlCommandType.SELECT,
                "SELECT * FROM chunks WHERE ("));
    }
}
