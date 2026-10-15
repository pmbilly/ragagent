package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ragagent.knowledge.repository.KnowledgeSpanRepository;

/**
 * K6：三种启动复位**各按自己守护的任务基础设施**判据（不再用 {@code REDIS_ADDR} 统一代理）。
 *
 * <p>旧实现按 {@code REDIS_ADDR} 非空跳过知识/摘要复位：只把 Redis 用于 IM/embed 等其它面
 * （未装配知识 Redis 面）的部署被误判成分布式 ⇒ 卡住的知识行永不复位（前端永远转圈）。</p>
 */
class StartupTaskRecoverySplitTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    @DisplayName("K6：知识面跳过（knowledge.redis-enabled=true）时，进程内同步队列的卡住行仍被复位")
    @SuppressWarnings("unchecked")
    void criteriaArePerSurface() {
        StartupTaskRecovery recovery = new StartupTaskRecovery(jdbc,
                mock(KnowledgeSpanRepository.class),
                true /* 知识 Redis 面已装配 */,
                false /* 数据源同步队列仍是进程内版 */);

        recovery.resetPendingTasks();

        // 知识/摘要面按 knowledge.redis-enabled 跳过：一行 SELECT 都不发
        verify(jdbc, never()).queryForList(any(String.class), any(Class.class), any(Object[].class));

        // 同步日志面按 datasource.redis-enabled 照常复位（进程内队列重启必丢任务）
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues()).as("同步日志的复位必须是 UPDATE sync_logs")
                .anyMatch(s -> s.contains("sync_logs"));
    }

    @Test
    @DisplayName("K6：知识面未装配 Redis（false）时照常复位卡住的知识行")
    @SuppressWarnings("unchecked")
    void knowledgeResetsWhenRedisSurfaceAbsent() {
        StartupTaskRecovery recovery = new StartupTaskRecovery(jdbc,
                mock(KnowledgeSpanRepository.class), false, false);
        when(jdbc.queryForList(any(String.class), any(Class.class), any(Object[].class)))
                .thenReturn(List.of());

        recovery.resetPendingTasks();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sql.capture(), any(Class.class), any(Object[].class));
        assertThat(sql.getAllValues()).as("知识行必须被挑出来复位")
                .anyMatch(s -> s.contains("knowledges"));
    }
}
