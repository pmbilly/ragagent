package com.ragagent.auth.service;

import com.ragagent.auth.api.UserPreferences;
import com.ragagent.auth.domain.AuthToken;
import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.Membership;
import com.ragagent.auth.dto.TenantRowView;
import com.ragagent.platform.text.UnicodeText;
import io.jsonwebtoken.Claims;
import java.util.List;

/**
 * 用户会话令牌操作：logout / refresh / switch-tenant。
 *
 * <p>三个失败通道异常（{@code UserService.LogoutException} 等）由控制器捕获并映射为响应，
 * 因此定义在 {@link UserService}；本类经 {@code userService} 访问令牌签发、偏好落库与
 * membership 组装。</p>
 */
final class UserSessionOps {

    private final UserService userService;

    UserSessionOps(UserService userService) {
        this.userService = userService;
    }

    /**
     * 从（可过期的）JWT 里解出 user_id，吊销该用户全部会话。
     *
     * <p>access / refresh 两种 token 都接受——客户端不必先 refresh 再登出。</p>
     *
     * @throws UserService.LogoutException message 为服务端错误原文，由调用方映射为响应
     */
    public void logout(String tokenString) {
        String userId = userIdFromSignedToken(tokenString);
        userService.revokeTokensByUserId(userId);
    }

    /** 不做 claims 校验的解析：过期令牌也能解出 user_id。 */
    private String userIdFromSignedToken(String tokenString) {
        Claims claims;
        try {
            claims = userService.jwtService.parseSignedAllowExpired(tokenString);
        } catch (TokenValidationException e) {
            throw new UserService.LogoutException("invalid token");
        }
        Object raw = claims.get("user_id");
        if (!(raw instanceof String userId) || UnicodeText.trim(userId).isEmpty()) {
            throw new UserService.LogoutException("invalid user ID in token");
        }
        return userId;
    }

    /**
     * 用未过期的 refresh token 换一对新令牌。
     *
     * <p>旧 refresh 一律吊销（一次性使用）；新令牌按 last-active 偏好解析目标空间。</p>
     *
     * @return 长度 2 的数组：[0] access token，[1] 新 refresh token
     * @throws UserService.RefreshTokenException message 为服务端错误原文，由调用方映射为响应
     */
    public String[] refreshToken(String refreshTokenString) {
        Claims claims;
        try {
            claims = userService.jwtService.parseSigned(refreshTokenString);
        } catch (TokenValidationException e) {
            // parseSigned 内含 exp 校验：过期与签名错误走同一分支
            throw new UserService.RefreshTokenException("invalid refresh token");
        }
        if (!JwtService.isRefreshTokenClaims(claims)) {
            throw new UserService.RefreshTokenException("not a refresh token");
        }
        Object rawUserId = claims.get("user_id");
        if (!(rawUserId instanceof String userId)) {
            throw new UserService.RefreshTokenException("invalid user ID in token");
        }

        // 记录缺失与已吊销返回同一文案：避免调用方借此探测令牌是否曾经存在
        AuthToken record = userService.findTokenByString(refreshTokenString);
        if (record == null || record.isIsRevoked()) {
            throw new UserService.RefreshTokenException("refresh token is revoked");
        }
        if (!UserService.TOKEN_TYPE_REFRESH.equals(record.getTokenType())) {
            throw new UserService.RefreshTokenException("not a refresh token");
        }

        User u = userService.getUserById(userId);
        if (u == null) {
            throw new UserService.RefreshTokenException("record not found");
        }

        // 吊销旧 refresh token（整行写回，updated_at 一并刷新）
        userService.revokeTokenRecord(record);

        // 按 last-active 偏好解析空间后签发
        return userService.generateTokens(u);
    }

    /**
     * 切换工作空间并签发新令牌对。
     *
     * <p>两条顺序约束：偏好先于签发落库（200 响应必须对应持久化成功）；旧 refresh 的吊销
     * 尽力而为，失败只记日志、不拖垮切换。跨空间超管切到自己的 home 时跳过成员校验。</p>
     *
     * @throws UserService.SwitchTenantException message 为服务端错误原文，由调用方映射为响应
     */
    public LoginResult switchTenant(User user, long targetTenantId, String currentRefreshToken) {
        if (user == null) {
            throw new UserService.SwitchTenantException("user is required");
        }
        if (targetTenantId == 0) {
            throw new UserService.SwitchTenantException("target workspace ID is required");
        }

        // 跨空间超管切到自己的 home 时跳过成员校验
        if (!user.isCanAccessAllTenants() || targetTenantId == user.getTenantId()) {
            TenantMember member = userService.memberService.getMembership(user.getId(), targetTenantId);
            if (member == null || !TenantMemberService.STATUS_ACTIVE.equals(member.getStatus())) {
                throw new UserService.SwitchTenantException("tenant membership not found");
            }
        }

        TenantRowView tenant = userService.tenantService.getTenantById(targetTenantId);
        if (tenant == null) {
            throw new UserService.SwitchTenantException("load target workspace: record not found");
        }

        // 先落偏好再签发（200 响应必须同时是持久的落地偏好更新）
        try {
            UserPreferences patch = new UserPreferences();
            patch.setLastActiveTenantId(targetTenantId);
            UserPreferences merged = userService.updateUserPreferences(user.getId(), patch);
            user.setPreferences(merged);
        } catch (RuntimeException e) {
            throw new UserService.SwitchTenantException("record last-active-tenant preference: " + e.getMessage(), e);
        }

        String[] tokens;
        try {
            tokens = userService.generateTokensForTenant(user, targetTenantId);
        } catch (RuntimeException e) {
            throw new UserService.SwitchTenantException("generate tokens: " + e.getMessage(), e);
        }

        // 尽力吊销旧 refresh（失败只记日志，不拖垮切换）
        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            try {
                AuthToken record = userService.findTokenByString(currentRefreshToken);
                if (record != null) {
                    userService.revokeTokenRecord(record);
                }
            } catch (RuntimeException e) {
                UserService.log.warn("Failed to revoke previous refresh token during tenant switch", e);
            }
        }

        List<Membership> memberships = userService.buildLoginMemberships(user, tenant);
        return new LoginResult(true, "Workspace switched", user, tenant, memberships,
                tokens[0], tokens[1]);
    }
}
