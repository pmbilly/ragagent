package com.ragagent.retrieval.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * K10b（登记项）：按位置配行的最后一道闸——行/向量数量不符必须在写库前抛出。
 *
 * <p>上游顺序与数量问题已在 {@code EmbedderClient.embedBatch} 收口（K10a），这里是
 * "调用方自己拼的两张表长度不一致"的兜底：错位/少条不许静默写错行。</p>
 */
class VectorStoreServiceIndexRowArityTest {

    private final JdbcTemplate jdbc = nonPostgresJdbc();
    private final VectorStoreService service = new VectorStoreService(jdbc);

    /** 构造需要探测方言（PG/H2 分支 SQL 不同），桩成 H2 即可。 */
    private static JdbcTemplate nonPostgresJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        try {
            when(ds.getConnection()).thenReturn(conn);
            when(conn.getMetaData()).thenReturn(meta);
            when(meta.getDatabaseProductName()).thenReturn("H2");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        when(jdbc.getDataSource()).thenReturn(ds);
        return jdbc;
    }

    private static VectorStoreService.IndexRow row(String id) {
        return new VectorStoreService.IndexRow(id, id, "kn", "kb", "text-" + id, true, "");
    }

    @Test
    @DisplayName("K10b：rows 与 vectors 数量不符 ⇒ 抛，且一行 SQL 都不发")
    void arityMismatchThrowsBeforeWriting() {
        assertThatThrownBy(() -> service.saveIndexRows(
                List.of(row("c1"), row("c2")), List.of(new float[] {1f, 2f})))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size mismatch");

        verify(jdbc, never()).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }

    @Test
    @DisplayName("K10b：null 也按不符处理（不静默空写）")
    void nullListsThrow() {
        assertThatThrownBy(() -> service.saveIndexRows(null, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size mismatch");

        verify(jdbc, never()).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }
}
