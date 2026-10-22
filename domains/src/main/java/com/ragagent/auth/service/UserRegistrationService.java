package com.ragagent.auth.service;

import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.TenantRowView;
import com.ragagent.auth.repository.UserRepository;
import com.ragagent.contract.auth.UserPreferences;
import com.ragagent.contract.tenant.TenantRole;
import com.ragagent.platform.security.LogSanitizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册用例：租户（含默认存储后端）→ 用户 → Owner 成员行，三步同一事务。
 *
 * <p>任一步失败整体回滚；错误文案由 controller 包成 400，逐字符保持既有契约。
 * 从 {@link UserService} 拆出后，注册是唯一需要事务的登录域写路径，门面只做委托。</p>
 */
@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepo;
    private final TenantService tenantService;
    private final TenantMemberService memberService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserRegistrationService(UserRepository userRepo, TenantService tenantService,
                                   TenantMemberService memberService) {
        this.userRepo = userRepo;
        this.tenantService = tenantService;
        this.memberService = memberService;
    }

    @Transactional(rollbackFor = Exception.class)
    public User register(String username, String email, String password, String provisioning) {
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            throw new UserService.RegistrationException(
                    "username, email and password are required");
        }
        if (userRepo.findByEmail(email) != null) {
            throw new UserService.RegistrationException("user with this email already exists");
        }
        if (userRepo.findByUsername(username) != null) {
            throw new UserService.RegistrationException("user with this username already exists");
        }
        String hashedPassword = passwordEncoder.encode(password);

        String mode = provisioning;
        if (mode == null || mode.isEmpty()) {
            mode = UserService.PROVISIONING_CREATE_PERSONAL;
        }
        if (!UserService.PROVISIONING_CREATE_PERSONAL.equals(mode)
                && !UserService.PROVISIONING_TENANTLESS.equals(mode)) {
            throw new UserService.RegistrationException(
                    "invalid tenant provisioning mode \"" + mode + "\"");
        }

        TenantRowView createdTenant = null;
        if (UserService.PROVISIONING_CREATE_PERSONAL.equals(mode)) {
            TenantRowView tenant = new TenantRowView();
            // 租户名再过一遍日志消毒（入口已对 username 消毒，此处幂等）
            tenant.setName(LogSanitizer.sanitize(username) + "'s Workspace");
            tenant.setDescription("Default workspace");
            tenant.setStatus("active");
            try {
                createdTenant = tenantService.createTenant(tenant);
            } catch (RuntimeException e) {
                log.error("Failed to create workspace", e);
                throw new UserService.RegistrationException("failed to create workspace");
            }
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hashedPassword);
        // tenantless 时 tenant_id 落库为 SQL NULL
        // （写 0 会违反 fk_users_tenant）；读回时由领域层归一为 0
        user.setTenantId(createdTenant != null ? createdTenant.getId() : null);
        user.setIsActive(true);
        user.setPreferences(new UserPreferences());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            if (createdTenant == null) {
                // tenantless：getter 会把 null 归一成 0，必须走省略 tenant_id 列的插入
                // （落 SQL NULL；写 0 在真 PG 违反 fk_users_tenant）
                userRepo.insertTenantless(user);
            } else {
                userRepo.insert(user);
            }
        } catch (RuntimeException e) {
            log.error("Failed to create user", e);
            throw new UserService.RegistrationException("failed to create user");
        }

        // Owner 成员行引导：失败由事务整体回滚
        if (createdTenant != null) {
            try {
                memberService.addMember(user.getId(), createdTenant.getId(), TenantRole.OWNER.value(), null);
            } catch (RuntimeException e) {
                log.error("Failed to create owner membership for user {} tenant {}",
                        user.getId(), createdTenant.getId(), e);
                throw new UserService.RegistrationException("failed to finalise workspace ownership");
            }
        }
        return user;
    }
}
