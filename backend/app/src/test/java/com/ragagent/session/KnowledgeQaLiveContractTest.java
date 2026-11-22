package com.ragagent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ragagent.TestSchema;
import com.ragagent.framework.tenant.TenantContext;
import com.ragagent.llm.api.ResponseType;
import com.ragagent.modelruntime.stream.StreamBatch;
import com.ragagent.modelruntime.stream.StreamEvent;
import com.ragagent.modelruntime.stream.StreamManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 平台入口（{@code /api/v1/knowledge-chat}）的 live 级契约——补 SseEmitter 迁移后的 CI 盲区。
 *
 * <p>覆盖三件事（{@code KnowledgeQaContractTest} 只校 golden 文件形状，不跑 live 路径）：
 * <ol>
 *   <li>校验错误在请求线程同步落 400 JSON 信封（bind 前移语义）；</li>
 *   <li>AGENT 模式撞 live run → 409 信封经异步错误派发渲染（completeError 链，
 *       拒绝发生在任何帧写出之前）；</li>
 *   <li><b>租户身份断言</b>：执行虚线程内 memo 的 {@code TenantContextSnapshot.capture()}
 *       带完整身份 → {@code sessions.agent_config} 被写——身份丢失时按 (tenant, owner)
 *       过滤后 0 行、列保持 null（迁移评审 F1 的静默缺陷形态）。</li>
 * </ol>
 *
 * <p>每个测试用独立会话 id；StreamManager 的 live run 存活跨测试，隔离靠会话而非清理顺序。
 * NORMAL 模式撞 live run 的形态是"200 流中断"（帧已写出、advice 无法回写 JSON，与旧实现同构），
 * 不在本类断言面内。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeQaLiveContractTest {

    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final String BCRYPT =
            "$2a$10$9U3ZmqQkmCqoQUZapJ1Txe5puo70IHlrnyZnSdE9LO/HUagt5exnK"; // Passw0rd!
    private static final long TENANT = 10002L;
    private static final String USER = "b0000000-0000-0000-0000-00000000c001";
    private static final String AGENT = "b1000000-0000-0000-0000-00000000c001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StreamManager streamManager;

    private String bearer;
    private String sessionId;

    @BeforeEach
    void seed() throws Exception {
        TestSchema.createTables(jdbc);
        TestSchema.resetData(jdbc);
        jdbc.update("INSERT INTO tenants (id, name, status) VALUES (?, 'qa-live-tenant', 'active')",
                TENANT);
        jdbc.update("INSERT INTO tenant_members (user_id, tenant_id, role, status) "
                + "VALUES (?, ?, 'contributor', 'active')", USER, TENANT);
        jdbc.update("INSERT INTO users (id, username, email, password_hash, tenant_id, is_active) "
                        + "VALUES (?, 'qa-live', 'qa-live@example.com', ?, ?, TRUE)",
                USER, BCRYPT, TENANT);
        jdbc.update("INSERT INTO custom_agents (id, name, tenant_id, config) "
                        + "VALUES (?, 'smart', ?, '{\"agentMode\":\"smart-reasoning\"}')",
                AGENT, TENANT);
        bearer = "Bearer " + login();
        // TenantContext 快照供服务层租户过滤（AuthFilter 在真实请求里同样会设）
        TenantContext.set(TENANT, new TenantContext.Principal(
                TenantContext.PrincipalTypes.WEB_USER, USER), "viewer", false, "qa-live", false);
        sessionId = "b5000000-0000-0000-0000-" + UUID.randomUUID().toString().substring(24);
        // 直插 SQL：repository.create 会用随机 UUID 覆盖 id，live 断言需要确定 id
        jdbc.update("INSERT INTO sessions (id, tenant_id, user_id, title) "
                        + "VALUES (?, ?, ?, 'live contract')",
                sessionId, TENANT, USER);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private String login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"qa-live@example.com\",\"password\":\"Passw0rd!\"}"))
                .andReturn();
        Matcher m = TOKEN.matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).as("login 响应应含 token: " + r.getResponse().getContentAsString()).isTrue();
        return m.group(1);
    }

    /** SSE 端点收束：异步启动则等收束后派发（completeWithError 的异常经解析器渲染）。 */
    private MvcResult settle(MvcResult r) throws Exception {
        if (r.getRequest().isAsyncStarted()) {
            r = mockMvc.perform(asyncDispatch(r)).andReturn();
        }
        return r;
    }

    private MvcResult postChat(String path, String body) throws Exception {
        return settle(mockMvc.perform(post(path)
                .header("Authorization", bearer)
                .contentType("application/json")
                .content(body)).andReturn());
    }

    @Test
    @DisplayName("校验错误在请求线程同步落 400 信封（bind 前移语义）")
    void missingQueryIs400Sync() throws Exception {
        MvcResult r = postChat("/api/v1/knowledge-chat/" + sessionId, "{}");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("field 'query' is required");
    }

    @Test
    @DisplayName("AGENT 模式撞 live run → 409 信封（帧前拒绝，虚线程 completeError 经异步错误派发渲染）")
    void agentTurnConflictIs409Envelope() throws Exception {
        System.out.println("[dbg-member] rows=" + jdbc.queryForList(
                "SELECT role, status FROM tenant_members WHERE user_id = ?", USER) + "\n");
        streamManager.setLiveRun(sessionId, "other-msg", "req-other");
        MvcResult r = postChat("/api/v1/agent-chat/" + sessionId,
                "{\"query\":\"hi\",\"agentId\":\"" + AGENT + "\",\"agentEnabled\":true}");
        System.out.println("[dbg-role] status=" + r.getResponse().getStatus()
                + " role=" + TenantContext.currentRole()
                + " body=" + r.getResponse().getContentAsString());
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(r.getResponse().getContentAsString())
                .contains("another turn is already running");
    }

    @Test
    @DisplayName("租户身份断言：409 前 memo 已在执行虚线程按 (tenant, owner) 写 lastRequestState")
    void tenantIdentitySurvivesVirtualThread() throws Exception {
        streamManager.setLiveRun(sessionId, "other-msg", "req-other");
        postChat("/api/v1/agent-chat/" + sessionId,
                "{\"query\":\"hi\",\"agentId\":\"" + AGENT + "\",\"agentEnabled\":true}");

        // memo（persistLastRequestStateAsync）先于 409 拒绝启动，是异步虚线程：
        // 轮询等 agent_config 被写入——身份丢失 → (tenant, owner) 过滤 0 行 → 恒 null
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<String> states = jdbc.queryForList(
                    "SELECT agent_config FROM sessions WHERE id = ?", String.class, sessionId);
            assertThat(states).as("memo 落库（虚线程身份完整）").isNotEmpty();
            assertThat(states.get(0)).as("lastRequestState 已写").isNotNull().contains("mode");
        });

        // StreamResponse/complete 语义的存在性口径（防 golden 漂移误读）
        assertThat(ResponseType.COMPLETE.name()).isEqualTo("COMPLETE");
        assertThat(new StreamBatch(List.<StreamEvent>of(), 0).events()).isEmpty();
    }
}
