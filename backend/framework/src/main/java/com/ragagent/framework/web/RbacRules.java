package com.ragagent.framework.web;

import com.ragagent.framework.tenant.TenantRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * RBAC 规则表与匹配：登记「方法 + Ant 路径 → 角色下限」，并按请求选出命中的规则。
 *
 * <p>只做匹配、不做判定——角色阶梯、系统管理员绕过、API Key 主体等决策仍在
 * {@link RbacInterceptor} 里，那里才拿得到当前主体。</p>
 *
 * <p><b>HEAD 兜底</b>：Spring 对 GET 自动派生 HEAD。若 HEAD 不按 GET 规则兜底，
 * 它会成为绕过方法级角色下限的旁路（只返回头，但泄露存在性与元数据），因此
 * {@link #match} 与 {@link #isPublic} 都在 HEAD 未命中时回落到 GET。</p>
 */
final class RbacRules {

    /**
     * 一条 RBAC 规则：方法 + Ant 路径 + 角色下限 + 是否允许系统管理员绕过 + 是否**仅限**系统管理员
     * + 是否跨空间守卫。
     *
     * <p>后三者是不同语义，别混：{@code orSystemAdmin} 是"租户角色达标**或**系统管理员"（放行条件），
     * {@code sysAdminOnly} 是"必须是系统管理员"（限定条件）。用前者表达后者会把租户 Owner 也放进来。
     * （注记：{@code crossTenant} 随空间分享裁撤已无规则置位，判定分支已移除。）</p>
     */
    record Rule(String method, String pattern, TenantRole minRole, boolean orSystemAdmin,
                boolean sysAdminOnly, boolean crossTenant) {
    }

    /** 公开/自服务路由：不设角色下限，鉴权由 AuthFilter、渠道 token、签名或所有权层负责。 */
    record PublicRule(String method, String pattern) {
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final List<Rule> rules = new ArrayList<>();

    private final List<PublicRule> publicRules = new ArrayList<>();

    void addRule(String method, String pattern, TenantRole minRole, boolean orSystemAdmin) {
        rules.add(new Rule(method, pattern, minRole, orSystemAdmin, false, false));
    }

    /** 仅系统管理员可访问（平台级端点，租户角色再高也不放行）。 */
    void addSystemAdminRule(String method, String pattern) {
        rules.add(new Rule(method, pattern, TenantRole.ADMIN, false, true, false));
    }

    void addPublicRule(String method, String pattern) {
        publicRules.add(new PublicRule(method, pattern));
    }

    boolean isPublic(String method, String uri) {
        if (matchPublic(method, uri) != null) {
            return true;
        }
        return "HEAD".equals(method) && matchPublic("GET", uri) != null;
    }

    PublicRule matchPublic(String method, String uri) {
        for (PublicRule rule : publicRules) {
            if (rule.method().equals(method) && MATCHER.match(rule.pattern(), uri)) {
                return rule;
            }
        }
        return null;
    }

    Rule match(String method, String uri) {
        Rule rule = matchExact(method, uri);
        if (rule == null && "HEAD".equals(method)) {
            rule = matchExact("GET", uri);
        }
        return rule;
    }

    private Rule matchExact(String method, String uri) {
        for (Rule rule : rules) {
            if (rule.method().equals(method) && MATCHER.match(rule.pattern(), uri)) {
                return rule;
            }
        }
        return null;
    }
}
