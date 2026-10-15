package com.ragagent.common.auth;

import java.util.List;

/**
 * 用户**管理面**端口（L1）：系统管理员侧的用户读 / 建 / 晋升 / 撤权 / 重置密码 / 密码策略。
 *
 * <p><b>为什么是端口</b>：该逻辑的载体是 auth 域的 {@code UserMapper} / {@code TenantService} /
 * {@code TenantMemberService}（§9.1 状态唯一写入者）；system 域的控制器只应经本端口，
 * 不再直引 auth 内部件（HANDOFF §12.6.4 C-13a 清偿 {@code system→auth} 边）。</p>
 *
 * <p><b>边界</b>：**设置读取不在本端口**（{@code complexPasswordEnabled}/{@code resolveDefaultTenantMode}
 * 属 system 侧壳）——本端口只收 {@code complexEnabled} 结果参数，避免与设置面成环。</p>
 *
 * <p>语义（错误消息与幂等分支是契约）与迁移前**逐字一致**：
 * promote 查无 → 404 "User not found"；revoke 自撤 / 末位管理员 → 400；create 身份完全一致 → 幂等。</p>
 */
public interface AdminUserPort {

    /** 分页结果（列系统管理员：count + created_at DESC, id ASC）。 */
    record AdminUserPage(List<AdminUserView> users, long total) {
    }

    /** 建用户结果三态（控制器折叠成 201/200/409）。 */
    sealed interface CreateResult {
        record Created(AdminUserView user, String generatedPassword) implements CreateResult {
        }

        record Idempotent(AdminUserView user) implements CreateResult {
        }
    }

    /** 身份部分冲突（email 与 username 各自命中不同行）。 */
    String ERR_USER_EMAIL_EXISTS = "user with this email already exists";
    String ERR_USER_USERNAME_EXISTS = "user with this username already exists";
    String ERR_USER_IDENTITY_CONFLICT = "email and username refer to conflicting existing identities";

    /** 按主键取未删行；空 id ⇒ null。 */
    AdminUserView byId(String id);

    /** 按 email 取未删行（controller 的 promote 前置查）。 */
    AdminUserView byEmail(String email);

    /** 按用户名取未删行（create 的身份判重）。 */
    AdminUserView byUsername(String username);

    /** count + created_at DESC, id ASC 分页（含 total）。 */
    AdminUserPage listSystemAdmins(int offset, int limit);

    /** 置 isSystemAdmin + updatedAt 并落库；返回回填后的视图。 */
    AdminUserView promote(AdminUserView user);

    /** 撤管理员（自撤 / 末位 → 400；查无 → 404；非管理员 → 幂等返回原行）。 */
    AdminUserView revoke(String userId, String callerId);

    /** 换 hash + 吊销该用户全部令牌（best-effort，异常记 WARN）。 */
    void resetPassword(String userId, String newPassword);

    /** 注册/建用户（含 create_personal 租户预配与 Owner 成员；失败回滚）。 */
    CreateResult createUser(String rawUsername, String rawEmail, String password,
                            boolean passwordPresent, String provisioning, boolean complexEnabled);


    /** 密码策略校验：null = 通过，否则为契约原文错误消息。 */
    String validatePasswordPolicy(String password, boolean complexEnabled);

    /** 生成过策略的密码（复杂 → 16 位复杂）。 */
    String generatePolicyCompliantPassword(boolean complexEnabled);

}
