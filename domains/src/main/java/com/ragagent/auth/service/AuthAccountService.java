package com.ragagent.auth.service;

import com.ragagent.auth.domain.User;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 认证账号写面：控制器不再持有 User 实体。 */
@Service
public class AuthAccountService {

    private static final Logger log = LoggerFactory.getLogger(AuthAccountService.class);

    private final UserService userService;
    private final TenantInvitationService invitationService;

    public AuthAccountService(UserService userService, TenantInvitationService invitationService) {
        this.userService = userService;
        this.invitationService = invitationService;
    }

    public Object completeInviteRegistration(Object rawUser, long tenantId, String token) {
        User user = (User) rawUser;
        user.setTenantId(tenantId);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            userService.updateUser(user);
        } catch (RuntimeException e) {
            try {
                userService.deleteUser(user.getId());
            } catch (RuntimeException ignored) {
                // 尽力清理半建账号
            }
            throw new BizException(AppError.internal("failed to finalise invited account")
                    .withDetails(e.getMessage()));
        }
        try {
            invitationService.acceptByToken(token, user.getId());
        } catch (RuntimeException e) {
            log.error("register-by-invite: accept failed for user {}", user.getId(), e);
            try {
                userService.restoreTenantless(user);
            } catch (RuntimeException rollbackErr) {
                log.error("register-by-invite: failed to restore tenantless user {}",
                        user.getId(), rollbackErr);
                try {
                    userService.deleteUser(user.getId());
                } catch (RuntimeException ignored) {
                    // 尽力
                }
            }
            throw new BizException(new AppError(1003,
                    "invitation link is no longer valid; please log in to your new account",
                    null, 410));
        }
        return user;
    }
}
