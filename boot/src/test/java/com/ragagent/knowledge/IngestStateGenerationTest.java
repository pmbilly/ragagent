package com.ragagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.TestSchema;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.repository.IngestStateRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 知识解析代次（parse_attempt）的状态 CAS 钉子：旧代次不得改写新代次的终态，
 * 也不得把已被 cancel 的行推进成 enabled。
 */
@SpringBootTest
class IngestStateGenerationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IngestStateRepository states;

    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void seed() {
        TestSchema.createTables(jdbc);
        TestSchema.resetData(jdbc);
    }

    private String knowledge(String parseStatus, long parseAttempt) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO knowledges (id, tenant_id, knowledge_base_id, type, title, "
                + "source, channel, parse_status, parse_attempt, enable_status, pending_subtasks_count, "
                + "summary_status, created_at, updated_at) "
                + "VALUES (?, 10002, 'kb-1', 'file', 't', 'file', '', ?, ?, 'disabled', 0, 'none', "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, parseStatus, parseAttempt);
        return id;
    }

    private String status(String id) {
        return jdbc.queryForObject("SELECT parse_status FROM knowledges WHERE id = ?", String.class, id);
    }

    private String enableStatus(String id) {
        return jdbc.queryForObject("SELECT enable_status FROM knowledges WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("旧代次抢不到 pending：CAS 返回 0，行仍是 pending")
    void staleAttemptCannotClaimPending() {
        String id = knowledge(Knowledge.PARSE_PENDING, 2L);

        assertThat(states.casPendingToProcessing(id, 1L, now)).isZero();
        assertThat(status(id)).isEqualTo(Knowledge.PARSE_PENDING);

        assertThat(states.casPendingToProcessing(id, 2L, now)).isOne();
        assertThat(status(id)).isEqualTo(Knowledge.PARSE_PROCESSING);
    }

    @Test
    @DisplayName("旧代次改不了终态：completed/failed 都只认当前代次")
    void staleAttemptCannotWriteTerminalState() {
        String id = knowledge(Knowledge.PARSE_PROCESSING, 2L);

        assertThat(states.completeIfOwned(id, 1L, false, now)).isZero();
        assertThat(states.failIfProcessing(id, 1L, "stale", now)).isZero();
        assertThat(status(id)).isEqualTo(Knowledge.PARSE_PROCESSING);

        assertThat(states.failIfProcessing(id, 2L, "boom", now)).isOne();
        assertThat(status(id)).isEqualTo(Knowledge.PARSE_FAILED);
    }

    @Test
    @DisplayName("cancel 抢走行后不再推进 enabled（cancelled 不会变成 cancelled+enabled）")
    void cancelledRowIsNotMarkedEnabled() {
        String id = knowledge(Knowledge.PARSE_CANCELLED, 2L);

        assertThat(states.markIndexedEnabledIfOwned(id, 2L, now)).isZero();
        assertThat(enableStatus(id)).isNotEqualTo("enabled");
    }

    @Test
    @DisplayName("finalizing 行仍可推进 enabled；旧代次不行")
    void finalizingOwnedRowCanBeMarkedEnabled() {
        String id = knowledge(Knowledge.PARSE_FINALIZING, 2L);

        assertThat(states.markIndexedEnabledIfOwned(id, 1L, now)).isZero();
        assertThat(states.markIndexedEnabledIfOwned(id, 2L, now)).isOne();
        assertThat(enableStatus(id)).isEqualTo("enabled");
    }
}
