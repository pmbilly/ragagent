package com.ragagent.system.service;

import com.ragagent.contract.apikey.APIKeyScopeContext;
import com.ragagent.contract.audit.AuditOutcome;
import com.ragagent.contract.audit.AuditWritePort;
import com.ragagent.contract.auth.AdminUserPort;
import com.ragagent.contract.auth.AdminUserView;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.web.JsonMappers;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 系统管理员 P0 用户管理的**系统侧壳**：审计写入 + 控制器委托面。
 *
 * <p><b>本刀（C-13a）后</b>：用户/租户/成员的实际读写都归 auth 域（{@link AdminUserPort} 的实现
 * {@code auth/api/AdminUserAdapter}）——system 不再直引 {@code auth.mapper.UserMapper} /
 * {@code TenantService} / {@code TenantMemberService}（HANDOFF §12.6.4）。本类只负责：</p>
 * <ul>
 *   <li>{@link #emitAdminAudit}：租户 0 + target user 的 best-effort 审计（失败记 WARN）；</li>
 *   <li>{@link #systemAuditActorRole}：主体角色（平台 API-Key → {@code platform_api_key}）；</li>
 *   <li>{@link #isValidEmail}：binding 的 required,email 等价校验；</li>
 *   <li>其余方法**逐字转发**端口（语义、错误消息与幂等分支留在实现侧，本类不改写）。</li>
 * </ul>
 */
@Service
public class SystemAdminUserService {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminUserService.class);

    /** 身份冲突错误原文（再导出自端口，供控制器 / 契约测试引用）。 */
    public static final String ERR_USER_EMAIL_EXISTS = AdminUserPort.ERR_USER_EMAIL_EXISTS;
    public static final String ERR_USER_USERNAME_EXISTS = AdminUserPort.ERR_USER_USERNAME_EXISTS;
    public static final String ERR_USER_IDENTITY_CONFLICT = AdminUserPort.ERR_USER_IDENTITY_CONFLICT;

    private final AdminUserPort adminUserPort;
    private final SystemSettingService settingService;
    private final AuditWritePort auditWriter;

    public SystemAdminUserService(AdminUserPort adminUserPort,
                                  SystemSettingService settingService,
                                  AuditWritePort auditWriter) {
        this.adminUserPort = adminUserPort;
        this.settingService = settingService;
        this.auditWriter = auditWriter;
    }

    // ── 委托面（逐字转发，不改语义） ──────────────────────────────────────

    public AdminUserView getUserById(String id) {
        return adminUserPort.byId(id);
    }

    public AdminUserView getUserByEmail(String email) {
        return adminUserPort.byEmail(email);
    }

    public AdminUserView getByUsername(String username) {
        return adminUserPort.byUsername(username);
    }

    public AdminUserPort.AdminUserPage listSystemAdmins(int offset, int limit) {
        return adminUserPort.listSystemAdmins(offset, limit);
    }

    public AdminUserView promote(AdminUserView user) {
        return adminUserPort.promote(user);
    }

    public AdminUserView revoke(String userId, String callerId) {
        return adminUserPort.revoke(userId, callerId);
    }

    public void adminResetPassword(AdminUserView user, String newPassword) {
        adminUserPort.resetPassword(user.getId(), newPassword);
    }

    public AdminUserPort.CreateResult adminCreateUser(String rawUsername, String rawEmail,
                                                     String password, boolean passwordPresent,
                                                     String provisioning) {
        return adminUserPort.createUser(rawUsername, rawEmail, password, passwordPresent, provisioning,
                complexPasswordEnabled());
    }

    public boolean complexPasswordEnabled() {
        return settingService.getBool(
                "auth.complex_password_enabled", "WEKNORA_AUTH_COMPLEX_PASSWORD_ENABLED", false);
    }

    public String validatePasswordPolicy(String password, boolean complexEnabled) {
        return adminUserPort.validatePasswordPolicy(password, complexEnabled);
    }

    public String generatePolicyCompliantPassword(boolean complexEnabled) {
        return adminUserPort.generatePolicyCompliantPassword(complexEnabled);
    }

    public String resolveDefaultTenantMode() {
        String def = "create_personal";
        String mode = settingService.getString(
                "auth.default_tenant_mode", "WEKNORA_AUTH_DEFAULT_TENANT_MODE", def);
        return "tenantless".equals(mode) ? "tenantless" : "create_personal";
    }

    // ── 审计 ──────────────────────────────────────────────────────────────

    /** 主体角色：平台 API-Key 主体 → "platform_api_key"。 */
    public static String systemAuditActorRole() {
        var scope = APIKeyScopeContext.current();
        if (scope != null && scope.isPlatform()) {
            return "platform_api_key";
        }
        return "system_admin";
    }

    /** tenant_id=0 + target user。details 可空 → {}。 */
    public void emitAdminAudit(String action, AdminUserView target, Map<String, Object> details) {
        if (auditWriter == null) {
            return;
        }
        var detailsNode = JsonMappers.lenient().valueToTree(
                details == null ? Map.of() : details);
        String targetId = target == null ? "" : target.getId();
        try {
            auditWriter.logBestEffort(new AuditWritePort.AuditRecord(
                    0L,
                    TenantContext.currentUserId() == null ? "" : TenantContext.currentUserId(),
                    systemAuditActorRole(),
                    action,
                    "user",
                    targetId,
                    targetId,
                    "",
                    "",
                    AuditOutcome.SUCCESS,
                    detailsNode.toString()));
        } catch (RuntimeException e) {
            log.warn("system admin audit write failed (ignored)", e);
        }
    }

    /** go-playground 的 email 校验同族正则（宽松域名，允许无 TLD）。 */
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile(
            "^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~.\\-]+@[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$");

    /** binding 的 required,email 等价（fail → 通用 400 文案）。 */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
