package com.ragagent.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.tenant.TenantRole;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.config.TenantProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RbacInterceptorHeadTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void headInheritsGetRoleFloor() throws Exception {
        TenantContext.set(1L, TenantContext.webUserPrincipal("u"), "viewer", false, "u", false);
        RbacInterceptor rbac = new RbacInterceptor(new TenantProperties(true, true, 10));
        rbac.addRule("GET", "/api/v1/protected", TenantRole.ADMIN, false);

        MockHttpServletRequest head = new MockHttpServletRequest("HEAD", "/api/v1/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(rbac.preHandle(head, response, new Object()))
                .as("HEAD 必须继承 GET 的角色下限，不能成为绕过路径")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void unmatchedApiPathIsDeniedByDefault() throws Exception {
        TenantContext.set(1L, TenantContext.webUserPrincipal("u"), "admin", false, "u", false);
        RbacInterceptor rbac = new RbacInterceptor(new TenantProperties(true, true, 10));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/unmapped");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(rbac.preHandle(request, response, handlerMethod()))
                .as("/api/v1/** 未命中规则的控制器方法必须默认拒绝")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("no route policy");
    }

    @Test
    void explicitPublicRuleAllowsRoute() throws Exception {
        TenantContext.set(1L, TenantContext.webUserPrincipal("u"), "viewer", false, "u", false);
        RbacInterceptor rbac = new RbacInterceptor(new TenantProperties(true, true, 10));
        rbac.addPublicRule("GET", "/api/v1/public/**");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(rbac.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rbacDisabledAllowsUnmatchedApiPath() throws Exception {
        TenantContext.set(1L, TenantContext.webUserPrincipal("u"), "viewer", false, "u", false);
        RbacInterceptor rbac = new RbacInterceptor(new TenantProperties(false, true, 10));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/unmapped");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(rbac.preHandle(request, response, new Object()))
                .as("enableRbac=false 的滚动窗口内不得因默认拒绝而 403")
                .isTrue();
    }

    private static Object handlerMethod() throws NoSuchMethodException {
        return new org.springframework.web.method.HandlerMethod(
                new ProbeController(), ProbeController.class.getDeclaredMethod("probe"));
    }

    static final class ProbeController {
        void probe() {
        }
    }

    @Test
    void nonApiPathIsNotSubjectToDefaultDeny() throws Exception {
        TenantContext.set(1L, TenantContext.webUserPrincipal("u"), "viewer", false, "u", false);
        RbacInterceptor rbac = new RbacInterceptor(new TenantProperties(true, true, 10));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(rbac.preHandle(request, response, new Object())).isTrue();
    }
}
