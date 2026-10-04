package com.ragagent.config;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ragagent.common.mybatis.FullTableWriteGuard;
import com.ragagent.common.mybatis.TenantFilterGuard;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link FullTableWriteGuard} 行为测试：探针 SQL 直接打防护拦截器；
 * 另一条用例锁定 {@link MybatisPlusConfig} 装配出的插件链顺序
 * （分页在前、防护殿后——MP 官方建议：改写 SQL 的在前，不改写的放最后）。
 */
class FullTableWriteGuardTest {

    private final FullTableWriteGuard guard = new FullTableWriteGuard();

    private static MappedStatement ms(String id, SqlCommandType type, String sql) {
        var cfg = new org.apache.ibatis.session.Configuration();
        return new MappedStatement.Builder(cfg, id, new StaticSqlSource(cfg, sql), type).build();
    }

    private void update(MappedStatement ms) {
        guard.beforeUpdate(null, ms, Collections.emptyMap());
    }

    @Test
    @DisplayName("无 WHERE 的 DELETE 被拦")
    void blocksFullTableDelete() {
        assertThatExceptionOfType(MybatisPlusException.class)
                .as("全表 DELETE 必须被防护拦截")
                .isThrownBy(() -> update(ms("probe.fullDelete", SqlCommandType.DELETE,
                        "DELETE FROM t_chunk")))
                .withMessageContaining("禁止全表 DELETE");
    }

    @Test
    @DisplayName("无 WHERE 的 UPDATE 被拦")
    void blocksFullTableUpdate() {
        assertThatExceptionOfType(MybatisPlusException.class)
                .as("全表 UPDATE 必须被防护拦截")
                .isThrownBy(() -> update(ms("probe.fullUpdate", SqlCommandType.UPDATE,
                        "UPDATE t_chunk SET status = 1")))
                .withMessageContaining("禁止全表 UPDATE");
    }

    @Test
    @DisplayName("带 WHERE 的 DELETE/UPDATE 正常放行")
    void allowsConditionalWrites() {
        assertThatCode(() -> update(ms("probe.condDelete", SqlCommandType.DELETE,
                "DELETE FROM t_chunk WHERE id = ?"))).doesNotThrowAnyException();
        assertThatCode(() -> update(ms("probe.condUpdate", SqlCommandType.UPDATE,
                "UPDATE t_chunk SET status = ? WHERE tenant_id = ?"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("方言 SQL 解析失败时 fail-open 放行（PG #- 操作符 JSqlParser 吃不下）")
    void failsOpenOnDialectSql() {
        assertThatCode(() -> update(ms("probe.pgDialect", SqlCommandType.UPDATE,
                "UPDATE t_chunk SET metadata = metadata #- '{a,b}' WHERE id = 1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("byId 族跳过防护（自带主键 WHERE）")
    void skipsByIdStatements() {
        assertThatCode(() -> update(ms("com.ragagent.knowledge.mapper.ChunkMapper.updateById",
                SqlCommandType.UPDATE, "UPDATE t_chunk SET status = 1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FULL_TABLE_ALLOWED 登记过的具名全表写放行")
    void allowsRegisteredFullTableStatements() {
        assertThatCode(() -> update(ms(
                "com.ragagent.auth.mapper.TenantMapper.applyDefaultStorageQuota",
                SqlCommandType.UPDATE, "UPDATE tenants SET storage_quota = ?")))
                .doesNotThrowAnyException();
        // 未登记的同类语句仍被拦——放行以语句 id 为界，不外溢
        assertThatExceptionOfType(MybatisPlusException.class)
                .isThrownBy(() -> update(ms("probe.unregistered", SqlCommandType.UPDATE,
                        "UPDATE tenants SET storage_quota = ?")));
    }

    @Test
    @DisplayName("非写语句不受影响")
    void ignoresNonWriteStatements() {
        assertThatCode(() -> update(ms("probe.select", SqlCommandType.SELECT,
                "SELECT * FROM t_chunk"))).doesNotThrowAnyException();
        assertThatCode(() -> update(ms("probe.insert", SqlCommandType.INSERT,
                "INSERT INTO t_chunk (id) VALUES (1)"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UPDATE ... WHERE 里带多个占位符且无参数绑定也能解析放行")
    void parsesConditionalWithPlaceholders() {
        assertThatCode(() -> update(ms("probe.placeholder", SqlCommandType.UPDATE,
                "UPDATE t_chunk SET deleted_at = now() WHERE knowledge_id = ? AND chunk_type IN (?, ?)")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("插件链装配顺序：分页在前、防护殿后、租户探测末位")
    void wiringOrder() {
        var chain = new MybatisPlusConfig()
                .mybatisPlusInterceptor(TenantFilterGuard.Mode.ALERT).getInterceptors();
        assertThat(chain).hasSize(3);
        InnerInterceptor pagination = chain.get(0);
        InnerInterceptor guard = chain.get(1);
        assertThat(pagination).isInstanceOf(PaginationInnerInterceptor.class);
        assertThat(guard).isInstanceOf(FullTableWriteGuard.class);
        assertThat(chain.get(2)).isInstanceOf(TenantFilterGuard.class);
    }
}
