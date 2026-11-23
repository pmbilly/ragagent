package com.ragagent.config.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.apikey.controller.filter.APIKeyGateResponses;
import com.ragagent.framework.config.TenantProperties;
import com.ragagent.framework.error.AppError;
import com.ragagent.framework.error.BizException;
import com.ragagent.framework.error.ErrorCode;
import com.ragagent.framework.jsonbind.JsonMappers;
import com.ragagent.framework.tenant.APIKeyScopeContext;
import com.ragagent.framework.tenant.TenantAPIKeyScope;
import com.ragagent.framework.tenant.TenantContext;
import com.ragagent.framework.tenant.TenantRole;
import com.ragagent.framework.web.ApiResponse;
import com.ragagent.framework.web.ApiResultSupport;
import com.ragagent.framework.web.DeniedAuditorRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 统一路由策略拦截器：原 {@code RbacInterceptor}（角色下限）与
 * {@code APIKeyGateInterceptor}（API-Key 能力维度）合一。
 *
 * <p><b>分派顺序</b>（与原双拦截器链语义逐字等价）：</p>
 * <ol>
 *   <li>ASYNC 派发跳过（SseEmitter completeError 的重入不做二次鉴权）；</li>
 *   <li>PathTenantMatch（URL 租户 == 活动租户，先于一切主体分派）；</li>
 *   <li>API-Key 主体 → 仅当路径落在原 APIKeyGate 覆盖面内才做能力判定
 *       （{@code /api/v1/**} 排除 {@code /api/v1/files/**} 与沙箱终端），
 *       覆盖面相匹配的规则与文案与原 APIKeyGateInterceptor 一致；
 *       不在覆盖面内则按原 RbacInterceptor 对 API-Key 主体短路放行；</li>
 *   <li>web（JWT）主体 → 角色下限矩阵（375 条，登记顺序即语义，首个命中生效）。</li>
 * </ol>
 *
 * <p><b>注册接口</b>：同时实现原 RbacInterceptor 的
 * {@code addRule/addSystemAdminRule/addPublicRule} 与原 APIKeyRouteAuthorizer 的
 * {@code register/registerGin}，使两张策略表（RbacRoutePolicies 375 条 +
 * APIKeyRoutePolicies 291 条）的注册调用逐字不变，仅 {@code registerAll} 的
 * 参数类型换为本类。</p>
 *
 * <p><b>拒绝审计</b>：拒绝分支经 {@code DeniedAuditorRegistry}
 * （framework/web 的进程级注册点，避免与 audit 域形成包级环）。</p>
 */
public class RoutePolicyInterceptor implements HandlerInterceptor {

    private static final ObjectMapper ENVELOPE_JSON =
            JsonMappers.lenient();

    private static final Logger log = LoggerFactory.getLogger(RoutePolicyInterceptor.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final RbacRules rbac = new RbacRules();
    private final APIKeyRouteAuthorizer authorizer = new APIKeyRouteAuthorizer();
    private final TenantProperties tenantProperties;

    public RoutePolicyInterceptor(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    // ── 注册接口：web 角色规则（RbacRoutePolicies 灌入，375 条） ──

    public RoutePolicyInterceptor addRule(String method, String pattern, TenantRole minRole, boolean orSystemAdmin) {
        rbac.addRule(method, pattern, minRole, orSystemAdmin);
        return this;
    }

    /** 仅系统管理员可访问（平台级端点，租户角色再高也不放行）。 */
    public RoutePolicyInterceptor addSystemAdminRule(String method, String pattern) {
        rbac.addSystemAdminRule(method, pattern);
        return this;
    }

    /** 公开/自服务路由：不设角色下限，鉴权由其它守卫负责。 */
    public RoutePolicyInterceptor addPublicRule(String method, String pattern) {
        rbac.addPublicRule(method, pattern);
        return this;
    }

    // ── 注册接口：API-Key 策略（APIKeyRoutePolicies 灌入，291 条） ──

    public void register(String method, String fullPath, APIKeyRoutePolicy policy) {
        authorizer.register(method, fullPath, policy);
    }

    public void registerGin(String method, String ginFullPath, APIKeyRoutePolicy policy) {
        authorizer.registerGin(method, ginFullPath, policy);
    }

    /** 只读查询面：守卫与测试用（registeredRoutes/declaredNodes/snapshot 等）。 */
    public APIKeyRouteAuthorizer authorizer() {
        return authorizer;
    }

    // ── 请求分派 ──

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // SseEmitter 化后的异步错误派发（completeError→ASYNC dispatch）会重跑本拦截器，
        // 而 Filter 的 finally 在异步启动时已清 TenantContext——此时角色恒空、必 403。
        // 首派发已完成 RBAC 强制；ASYNC 派发只做错误渲染，不再校验。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        // URL 里的租户 id 必须等于活动租户（对 Key 主体同样生效，先于主体分派）。
        if (!checkPathTenantMatch(request, response)) {
            return false;
        }
        if (APIKeyScopeContext.present()) {
            return handleApiKeyPrincipal(request, response);
        }
        return handleWebPrincipal(request, response, handler);
    }

    /**
     * API-Key 主体分派：仅当路径落在原 APIKeyGateInterceptor 覆盖面内才做能力判定；
     * 不在覆盖面（{@code /api/v1/files/**}、沙箱终端）按原 RbacInterceptor 短路放行。
     */
    private boolean handleApiKeyPrincipal(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getRequestURI();
        if (!isApiKeyGateCoveredPath(uri)) {
            return true;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String fullPath = pattern == null ? uri : pattern.toString();
        if (authorizer.authorize(APIKeyScopeContext.current(), request.getMethod(), fullPath)) {
            return true;
        }
        return APIKeyGateResponses.writeForbidden(response, 403, APIKeyGateResponses.SCOPE_FORBIDDEN);
    }

    /** web（JWT）主体的角色下限判定（原 RbacInterceptor.preHandle 的 web 部分）。 */
    private boolean handleWebPrincipal(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        RbacRules.Rule rule = rbac.match(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            if (rbac.isPublic(request.getMethod(), request.getRequestURI())) {
                return true;
            }
            // 只对"真实控制器方法"默认拒绝；未映射路径继续由 Spring 返回 404。
            if (request.getRequestURI().startsWith("/api/v1/")
                    && handler instanceof HandlerMethod) {
                if (!tenantProperties.isRbacEnforced()) {
                    log.warn("[rbac] unmatched route (logged but not enforced): user={} path={}",
                            TenantContext.currentUserId(), request.getRequestURI());
                    return true;
                }
                return denyUnmatched(request, response);
            }
            return true;
        }
        if (check(rule)) {
            return true;
        }
        log.warn("[rbac] role insufficient: user={} have={} need={} path={}",
                TenantContext.currentUserId(), TenantContext.currentRole(),
                rule.minRole().value(), request.getRequestURI());
        try {
            Long tenantId = TenantContext.currentTenantId();
            DeniedAuditorRegistry.deniedAuditor().logDenied(
                    tenantId == null ? 0L : tenantId,
                    TenantContext.currentUserId(),
                    TenantContext.currentRole(),
                    rule.sysAdminOnly() ? "system_admin" : rule.minRole().value(),
                    routeTemplate(request, rule),
                    request.getMethod(),
                    request.getRequestURI());
        } catch (RuntimeException e) {
            log.warn("[rbac] denied-audit write failed (ignored): path={}", request.getRequestURI(), e);
        }
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = rule.sysAdminOnly()
                ? "Forbidden: system administrator required"
                : "Forbidden: insufficient workspace role";
        if (ApiResultSupport.isActive(request)) {
            response.getWriter().write(forbiddenEnvelope(message));
            return false;
        }
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }

    /** 原 APIKeyGateInterceptor 的覆盖面：/api/v1/** 排除 /files 与沙箱终端。 */
    private static boolean isApiKeyGateCoveredPath(String uri) {
        if (!uri.startsWith("/api/v1/")) {
            return false;
        }
        if (MATCHER.match("/api/v1/files/**", uri)) {
            return false;
        }
        return !MATCHER.match("/api/v1/sessions/*/sandbox/terminal", uri);
    }

    private boolean denyUnmatched(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("[rbac] no route policy: user={} path={}",
                TenantContext.currentUserId(), request.getRequestURI());
        try {
            Long tenantId = TenantContext.currentTenantId();
            DeniedAuditorRegistry.deniedAuditor().logDenied(
                    tenantId == null ? 0L : tenantId,
                    TenantContext.currentUserId(),
                    TenantContext.currentRole(),
                    "unmatched",
                    routeTemplate(request),
                    request.getMethod(),
                    request.getRequestURI());
        } catch (RuntimeException e) {
            log.warn("[rbac] denied-audit write failed (ignored): path={}", request.getRequestURI(), e);
        }
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = "Forbidden: no route policy";
        if (ApiResultSupport.isActive(request)) {
            response.getWriter().write(forbiddenEnvelope(message));
            return false;
        }
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }

    private boolean check(RbacRules.Rule rule) {
        // API-key 主体由 APIKeyGate 全权判定（能力 + KB 白名单 + default-deny），
        // 角色阶梯不适用于机器主体——角色下限规则短路放行；
        // sysAdminOnly 规则只放行平台 Key、拒绝租户 Key。
        // 注：preHandle 里 API-Key 主体已分派走，此处 apiKeyScope 恒为 null；
        // 分支保留作防御（若未来改动分派顺序，本方法仍正确）。
        TenantAPIKeyScope apiKeyScope =
                APIKeyScopeContext.current();
        if (apiKeyScope != null) {
            if (rule.sysAdminOnly()) {
                if (apiKeyScope.isPlatform()) {
                    return true;
                }
                log.warn("[rbac] system admin required: API-key principal denied path-rule={}",
                        rule.pattern());
                return false;
            }
            return true;
        }
        if (rule.sysAdminOnly()) {
            if (TenantContext.isSystemAdmin()) {
                return true;
            }
            if (!tenantProperties.isRbacEnforced()) {
                log.warn("[rbac] system-admin required (logged but not enforced): user={} path-rule={}",
                        TenantContext.currentUserId(), rule.pattern());
                return true;
            }
            return false;
        }
        if (rule.orSystemAdmin() && TenantContext.isSystemAdmin()) {
            return true;
        }
        TenantRole role = TenantRole.fromString(TenantContext.currentRole());
        if (role.hasPermission(rule.minRole())) {
            return true;
        }
        if (!tenantProperties.isRbacEnforced()) {
            log.warn("[rbac] role insufficient (logged but not enforced): user={} have={} need={}",
                    TenantContext.currentUserId(), role.value(), rule.minRole().value());
            return true;
        }
        return false;
    }

    /**
     * 对 {@code /api/v1/tenants/{id}/**} 强制 URL 里的租户 == 调用方活动租户。
     * 语义与原 RbacInterceptor.checkPathTenantMatch 逐字一致。
     */
    private boolean checkPathTenantMatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/tenants/")) {
            return true;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String p) || !p.startsWith("/api/v1/tenants/{")) {
            return true;
        }
        String rest = uri.substring("/api/v1/tenants/".length());
        int slash = rest.indexOf('/');
        String raw = (slash < 0 ? rest : rest.substring(0, slash)).trim();

        if (raw.isEmpty()) {
            throw new BizException(AppError.validation("workspace id is required"));
        }
        long pathTenantId;
        try {
            pathTenantId = Long.parseLong(raw);
            if (pathTenantId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new BizException(AppError.validation("workspace id must be a positive integer"));
        }

        Long ctxTenantId = TenantContext.currentTenantId();
        if (ctxTenantId == null || ctxTenantId == 0) {
            log.warn("[rbac] path-tenant-match: no tenant in ctx, path={}", uri);
            throw new BizException(AppError.unauthorized("workspace context missing"));
        }
        if (pathTenantId == ctxTenantId) {
            return true;
        }
        TenantAPIKeyScope keyScope =
                APIKeyScopeContext.current();
        if (keyScope != null && keyScope.isPlatform()) {
            return true;
        }
        log.warn("[rbac] path-tenant-match rejected: user={} ctx_tenant={} path_tenant={} path={}",
                TenantContext.currentUserId(), ctxTenantId, pathTenantId, uri);
        throw new BizException(AppError.forbidden(
                "Access denied: URL workspace does not match the active workspace"));
    }

    private static String routeTemplate(HttpServletRequest request, RbacRules.Rule rule) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isEmpty()) {
            return s;
        }
        return rule.pattern();
    }

    private static String routeTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isEmpty()) {
            return s;
        }
        return "unmatched";
    }

    /** 403 的统一外壳体（{@code {code:1002,message,data:null}}）；序列化失败回落到等价字符串。 */
    private static String forbiddenEnvelope(String message) {
        try {
            return ENVELOPE_JSON.writeValueAsString(
                    ApiResponse.fail(ErrorCode.FORBIDDEN.value(), message));
        } catch (RuntimeException | JsonProcessingException e) {
            return "{\"code\":1002,\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
