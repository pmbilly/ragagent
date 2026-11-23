package com.ragagent.framework.web;

/**
 * 拒绝审计回调的进程级注册点。
 *
 * <p>从原 RbacInterceptor 的内部接口与静态字段拆出：拦截器（config/route）与
 * 审计注册器（audit/service）分居两个包，若注册点留在拦截器上会形成
 * audit⇄config 的包级环。收口到 framework/web 后两边都只依赖框架层。</p>
 *
 * <p>{@code requestPath} 传路由模板（Spring 的
 * {@code HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE}），
 * 语义与原 RbacInterceptor.DeniedAuditor 完全一致。</p>
 */
public final class DeniedAuditorRegistry {

    /** 拒绝审计回调。 */
    @FunctionalInterface
    public interface DeniedAuditor {
        void logDenied(long tenantId, String actorUserId, String actorRole,
                       String requiredRole, String requestPath, String requestMethod, String rawPath);
    }

    private static final DeniedAuditor NOOP =
            (tenantId, actorUserId, actorRole, requiredRole, requestPath, requestMethod, rawPath) -> {};

    private static volatile DeniedAuditor deniedAuditor = NOOP;

    private DeniedAuditorRegistry() {
    }

    public static void setDeniedAuditor(DeniedAuditor auditor) {
        deniedAuditor = auditor == null ? NOOP : auditor;
    }

    public static DeniedAuditor deniedAuditor() {
        return deniedAuditor;
    }
}
