package com.ragagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.TestSchema;
import com.ragagent.knowledge.domain.UserKbPin;
import com.ragagent.knowledge.repository.UserKbPinRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 置顶（user_kb_pins）的租户隔离：写入必须带 tenant_id，且跨租户互不可见。
 *
 * <p>此前实体没有 tenant_id 字段，生产列是 {@code NOT NULL} 无默认 ⇒ 真 PG 上点置顶会失败；
 * H2 测试库也没建该列，所以问题一直没暴露。本测试锁住三点：插入落库的租户、同租户可读、
 * 跨租户不可见也不可删。</p>
 */
@SpringBootTest
class UserKbPinTenantIsolationTest {

    private static final long TENANT_A = 10001L;
    private static final long TENANT_B = 10002L;
    private static final String USER = "11111111-2222-3333-4444-555555555501";
    private static final String KB = "fa900001-0000-0000-0000-000000000001";

    @Autowired
    private UserKbPinRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestSchema.createTables(jdbc);
        TestSchema.resetData(jdbc);
    }

    private static UserKbPin pin(long tenantId) {
        UserKbPin pin = new UserKbPin();
        pin.setTenantId(tenantId);
        pin.setUserId(USER);
        pin.setKnowledgeBaseId(KB);
        pin.setPinnedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pin;
    }

    @Test
    void pinIsScopedToItsTenant() {
        repository.insert(pin(TENANT_A));

        assertThat(jdbc.queryForObject(
                "SELECT tenant_id FROM user_kb_pins WHERE user_id = ?", Long.class, USER))
                .isEqualTo(TENANT_A);

        assertThat(repository.findByUserAndKb(TENANT_A, USER, KB)).isNotNull();
        assertThat(repository.findByUserAndKb(TENANT_B, USER, KB)).isNull();
    }

    @Test
    void deleteInAnotherTenantDoesNotRemoveThePin() {
        repository.insert(pin(TENANT_A));

        repository.deleteByUserAndKb(TENANT_B, USER, KB);

        assertThat(repository.findByUserAndKb(TENANT_A, USER, KB)).isNotNull();
    }
}
