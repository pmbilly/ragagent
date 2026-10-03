package com.ragagent.mcp.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ragagent.TestSchema;
import com.ragagent.common.error.BizException;
import com.ragagent.mcp.domain.McpService;
import com.ragagent.mcp.mapper.McpServiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MCP 工具审批的服务层契约。
 *
 * <p>语义核心：SetEnabled 保留 approval、SetRequireApproval 保留 disabled——
 * 两条都用同一条部分列补丁路径实现。</p>
 *
 * <p>夹具差异：直接落库（mcp_services 一行）以满足"服务存在"检查、复用同一份装配。
 * "批量读取只发一条查询"的次数断言属于 agent 审批门（{@code Gate}），不在本文件范围；
 * 此处只覆盖策略仓储/服务本身（批量读取由 {@code listByService} 一次查询完成）。</p>
 */
@SpringBootTest
class McpToolApprovalServiceTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private McpToolApprovalService svc;
    @Autowired
    private McpServiceMapper mcpServiceMapper;

    @BeforeEach
    void setUp() {
        TestSchema.createTables(jdbc);
        TestSchema.resetData(jdbc);

        McpService service = new McpService();
        service.setId("svc-1");
        service.setTenantId(1L);
        service.setName("weather");
        service.setEnabled(true);
        service.setTransportType("sse");
        mcpServiceMapper.insert(service);
    }

    @Test
    void setEnabledPreservesApproval() {
        svc.setRequireApproval(1, "svc-1", "get_forecast", true);
        svc.setEnabled(1, "svc-1", "get_forecast", false);

        assertTrue(svc.isRequired(1, "svc-1", "get_forecast"));
        assertFalse(svc.isEnabled(1, "svc-1", "get_forecast"));
    }

    @Test
    void setRequireApprovalPreservesDisabled() {
        svc.setEnabled(1, "svc-1", "get_forecast", false);
        svc.setRequireApproval(1, "svc-1", "get_forecast", true);

        assertFalse(svc.isEnabled(1, "svc-1", "get_forecast"));
        assertTrue(svc.isRequired(1, "svc-1", "get_forecast"));
    }

    @Test
    void setPolicyWritesBothFieldsOnce() {
        svc.setPolicy(1, "svc-1", "get_forecast", true, false);

        assertTrue(svc.isRequired(1, "svc-1", "get_forecast"));
        assertFalse(svc.isEnabled(1, "svc-1", "get_forecast"));
    }

    @Test
    void setPolicyRejectsEmptyPatch() {
        assertThrows(BizException.class,
                () -> svc.setPolicy(1, "svc-1", "get_forecast", null, null));
        assertThrows(BizException.class,
                () -> svc.setPolicy(1, "svc-1", "", true, null));
    }

    @Test
    void unknownService() {
        BizException e = assertThrows(BizException.class,
                () -> svc.setEnabled(1, "missing", "tool", false));
        assertTrue(e.getMessage().contains("not found"), "未知服务必须报 not found");

        assertThrows(BizException.class, () -> svc.listByService(1, "missing"));
    }

    @Test
    void missingPolicyReadsAsEnabledNotRequired() {
        assertTrue(svc.isEnabled(1, "svc-1", "never-seen"));
        assertFalse(svc.isRequired(1, "svc-1", "never-seen"));
    }
}
