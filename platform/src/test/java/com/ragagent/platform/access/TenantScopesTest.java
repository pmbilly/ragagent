package com.ragagent.platform.access;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TenantScopes} 的作用域语义：进入 / 恢复（含访客与请求 id）/ 不越界改动。 */
class TenantScopesTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("enter：进入租户并清空身份；close 恢复调用线程的完整原会话")
    void enterAndRestoreFullSession() {
        TenantContext.set(11L, TenantContext.webUserPrincipal("u-1"), "owner", true, "u-1", true);
        TenantContext.setEmbedVisitorId("visitor-9");
        TenantContext.setRequestId("req-9");

        try (TenantScopes.TenantScope ignored = TenantScopes.enter(22L)) {
            assertEquals(22L, TenantContext.currentTenantId());
            assertNull(TenantContext.currentPrincipal());
            assertNull(TenantContext.currentRole());
            assertNull(TenantContext.currentUserId());
        }

        assertEquals(11L, TenantContext.currentTenantId());
        assertEquals("u-1", TenantContext.currentUserId());
        assertEquals("owner", TenantContext.currentRole());
        assertEquals("visitor-9", TenantContext.currentEmbedVisitorId());
        assertEquals("req-9", TenantContext.currentRequestId());
    }

    @Test
    @DisplayName("enter(0) / enterCaptured(null)：不改动上下文（空租户不猜测）")
    void nonPositiveTenantDoesNotClobber() {
        TenantContext.set(11L, null, "owner", false, "u-1", false);

        try (TenantScopes.TenantScope a = TenantScopes.enter(0);
                TenantScopes.TenantScope b = TenantScopes.enterCaptured(null)) {
            assertEquals(11L, TenantContext.currentTenantId());
            assertEquals("owner", TenantContext.currentRole());
        }

        assertEquals(11L, TenantContext.currentTenantId());
    }

    @Test
    @DisplayName("嵌套：内层 close 回到外层租户，外层 close 回到空")
    void nestedScopesRestoreOuter() {
        try (TenantScopes.TenantScope outer = TenantScopes.enter(11L)) {
            assertEquals(11L, TenantContext.currentTenantId());
            try (TenantScopes.TenantScope inner = TenantScopes.enter(22L)) {
                assertEquals(22L, TenantContext.currentTenantId());
            }
            assertEquals(11L, TenantContext.currentTenantId());
        }
        assertNull(TenantContext.currentTenantId());
    }
}
