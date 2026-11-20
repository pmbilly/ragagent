package com.ragagent.auth.service;

import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录目标空间解析：last-active 偏好 → home → 首个 active membership。
 *
 * <p>失效自愈会写库（清 tenant_id / 清偏好），两个坑写在方法注释里：
 * {@code tenant_id=0} 会炸 FK、jsonb 两参 set 会触发 MyBatisSystemException。</p>
 */
final class LoginTenantResolver {

    private static final Logger log = LoggerFactory.getLogger(LoginTenantResolver.class);

    private final UserRepository userRepo;
    private final TenantService tenantService;
    private final TenantMemberService memberService;

    LoginTenantResolver(UserRepository userRepo, TenantService tenantService,
                        TenantMemberService memberService) {
        this.userRepo = userRepo;
        this.tenantService = tenantService;
        this.memberService = memberService;
    }

    /** 解析登录目标空间：last-active 偏好优先，偏好失效即清除并回退。 */
    long resolve(User user) {
        if (user == null) {
            return 0;
        }
        Long pref = user.getPreferences() != null
                ? user.getPreferences().getLastActiveTenantId() : null;
        long home = user.getTenantId() == null ? 0 : user.getTenantId();
        if (pref == null || pref == 0 || pref == home) {
            return homeOrFirstMembershipTenant(user);
        }
        long preferred = pref;

        if (tenantService.getTenantById(preferred) == null) {
            log.warn("resolveLoginTenantID: preferred tenant {} not loadable for user {}, "
                    + "clearing preference and falling back to home", preferred, user.getId());
            clearLastActiveTenantPreference(user);
            return homeOrFirstMembershipTenant(user);
        }

        if (!user.isCanAccessAllTenants()) {
            TenantMember member = memberService.getMembership(user.getId(), preferred);
            if (member == null || !TenantMemberService.STATUS_ACTIVE.equals(member.getStatus())) {
                log.warn("resolveLoginTenantID: user {} no longer has active membership in tenant {}, "
                        + "clearing preference and falling back to home", user.getId(), preferred);
                clearLastActiveTenantPreference(user);
                return homeOrFirstMembershipTenant(user);
            }
        }
        return preferred;
    }

    /** home 空间优先；无 home 或 home 失去 active 成员资格时回退首个 membership。 */
    private long homeOrFirstMembershipTenant(User user) {
        if (user == null) {
            return 0;
        }
        long home = user.getTenantId() == null ? 0 : user.getTenantId();
        if (home == 0) {
            return resolveFirstMembershipTenant(user);
        }
        if (user.isCanAccessAllTenants()) {
            return home;
        }
        TenantMember member = memberService.getMembership(user.getId(), home);
        if (member != null && TenantMemberService.STATUS_ACTIVE.equals(member.getStatus())) {
            return home;
        }
        log.warn("homeOrFirstMembershipTenant: user {} home tenant {} has no active membership, "
                + "clearing stale home and re-resolving", user.getId(), home);
        clearStaleHomeTenant(user);
        return resolveFirstMembershipTenant(user);
    }

    /** 清掉失效 home：tenant_id 归零并落库（失败仅记日志）。 */
    private void clearStaleHomeTenant(User user) {
        long staleHome = user.getTenantId() == null ? 0 : user.getTenantId();
        user.setTenantId(0L);
        if (user.getPreferences() != null
                && staleHome != 0
                && Long.valueOf(staleHome).equals(user.getPreferences().getLastActiveTenantId())) {
            user.getPreferences().setLastActiveTenantId(null);
        }
        try {
            // tenant_id 置空必须显式写 SQL NULL。
            // tenant_id=0 写进 UPDATE 会炸 FK fk_users_tenant；preferences 是
            // jsonb 列，两参 set 带 typeHandler 字段会 MyBatisSystemException（须用三参 set）。
            // 只写 tenant_id=NULL——preferences.last_active_tenant_id 的存量偏差无害
            // （pref==home 与 home 走同一条解析路径，净行为一致；已记录为已知偏差）。
            userRepo.clearTenantId(user.getId());
        } catch (RuntimeException e) {
            log.warn("clearStaleHomeTenant: failed to persist cleared home for user {} (was tenant {})",
                    user.getId(), staleHome, e);
        }
    }

    /**
     * tenantless 身份采用最早 active membership，
     * 并尽力持久化为 home（持久化失败不阻塞，仍返回该 membership 的 tenant）。
     */
    private long resolveFirstMembershipTenant(User user) {
        if (user == null) {
            return 0;
        }
        List<TenantMember> members = memberService.listByUser(user.getId());
        for (TenantMember member : members) {
            if (member == null
                    || member.getTenantId() == null || member.getTenantId() == 0
                    || !TenantMemberService.STATUS_ACTIVE.equals(member.getStatus())) {
                continue;
            }
            if (tenantService.getTenantById(member.getTenantId()) == null) {
                log.warn("resolveLoginTenantID: tenant {} for tenantless user {} is unavailable",
                        member.getTenantId(), user.getId());
                continue;
            }
            long tid = member.getTenantId();
            user.setTenantId(tid);
            try {
                userRepo.updateById(user);
            } catch (RuntimeException e) {
                log.warn("resolveLoginTenantID: failed to persist tenant {} for tenantless user {}",
                        tid, user.getId(), e);
                user.setTenantId(0L);
            }
            return tid;
        }
        return 0;
    }

    /** 清 last-active 偏好并落库（失败仅记日志）。 */
    private void clearLastActiveTenantPreference(User user) {
        if (user.getPreferences() != null) {
            user.getPreferences().setLastActiveTenantId(null);
        }
        try {
            userRepo.updateById(user);
        } catch (RuntimeException e) {
            log.warn("clearLastActiveTenantPreference: failed to persist cleared preference for user {}",
                    user.getId(), e);
        }
    }
}
