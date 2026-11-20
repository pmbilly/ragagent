package com.ragagent.im.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ragagent.contract.agent.AgentLookup;
import com.ragagent.im.domain.ImChannelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ImChannelService#setChannelAgentId} 的契约钉子（A③-⑰ 换绑校验改走 {@link AgentLookup} 后）：
 * 三条对外路径——存在 ⇒ 落 id；不存在/跨租户 ⇒ {@code AgentNotFoundException}
 * （HTTP 层 404 "agent not found"）；空 id ⇒ {@code IllegalArgumentException}（400 "agent_id is required"）。
 */
class ImChannelServiceTest {

    private static ImChannelService service(AgentLookup lookup) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ImTurnOrchestrator> runtime = mock(ObjectProvider.class);
        return new ImChannelService(null, lookup, runtime);
    }

    private static ImChannelEntity channel(long tenantId) {
        ImChannelEntity ch = new ImChannelEntity();
        ch.setId("ch1");
        ch.setTenantId(tenantId);
        return ch;
    }

    @Test
    @DisplayName("换绑：行存在且租户匹配 ⇒ 落 agentId；不存在 ⇒ AgentNotFoundException")
    void binding() {
        AgentLookup lookup = mock(AgentLookup.class);
        ImChannelService svc = service(lookup);

        when(lookup.hasAgentRow(13L, "a1")).thenReturn(true);
        ImChannelEntity ch = channel(13L);
        svc.setChannelAgentId(ch, "a1");
        assertEquals("a1", ch.getAgentId());

        ImChannelEntity ch2 = channel(13L);
        assertThrows(ImChannelService.AgentNotFoundException.class,
                () -> svc.setChannelAgentId(ch2, "missing"));
    }

    @Test
    @DisplayName("换绑：空/空白 agentId ⇒ IllegalArgumentException（agent_id is required）")
    void blankRejected() {
        ImChannelService svc = service(mock(AgentLookup.class));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> svc.setChannelAgentId(channel(13L), "   "));
        assertEquals("agent_id is required", e.getMessage());
    }
}
