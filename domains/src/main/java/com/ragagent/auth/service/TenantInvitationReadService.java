package com.ragagent.auth.service;

import com.ragagent.auth.domain.TenantInvitation;
import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.InvitationLookupView;
import com.ragagent.auth.dto.TenantInvitationResponse;
import com.ragagent.auth.dto.TenantRowView;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.error.ErrorCode;
import com.ragagent.contract.tenant.TenantRole;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 邀请控制面读模型：投影、用户/租户 hydrate、auto-accept 与成员响应。 */
@Service
public class TenantInvitationReadService {

    private static final String FRONTEND_BASE_URL_PROPERTY = "FRONTEND_BASE_URL";

    private final TenantInvitationService invitationService;
    private final UserService userService;
    private final TenantService tenantService;
    private final TenantMemberReadService memberReadService;
    private final Environment environment;

    public TenantInvitationReadService(TenantInvitationService invitationService,
            UserService userService, TenantService tenantService,
            TenantMemberReadService memberReadService, Environment environment) {
        this.invitationService = invitationService;
        this.userService = userService;
        this.tenantService = tenantService;
        this.memberReadService = memberReadService;
        this.environment = environment;
    }

    public TenantMemberReadService.PreparedResponse autoAcceptInvitationAndRespond(
            User user, long tenantId, TenantRole role, String invitedBy) {
        TenantMemberReadService.PreparedResponse r = memberReadService.addMemberAndRespond(
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatar(),
                tenantId, role, invitedBy);
        invitationService.markPendingAcceptedIfExists(tenantId, user.getId());
        if (user.getTenantId() == null || user.getTenantId() == 0) {
            user.setTenantId(tenantId);
            try {
                userService.updateUser(user);
            } catch (RuntimeException e) {
                throw new BizException(AppError.internal(
                        "member added but default workspace update failed")
                        .withDetails(e.getMessage()));
            }
        }
        return r;
    }

    public void adoptHomeTenantIfTenantless(String caller, TenantMember member) {
        User user = userService.getUserById(caller);
        if (user != null && (user.getTenantId() == null || user.getTenantId() == 0)) {
            user.setTenantId(member.getTenantId());
            try {
                userService.updateUser(user);
            } catch (RuntimeException e) {
                throw new BizException(AppError.internal(
                        "invitation accepted but default workspace update failed")
                        .withDetails(e.getMessage()));
            }
        }
    }

    public TenantInvitationResponse projectInvitation(TenantInvitation inv,
            Map<String, User> usersById, Map<Long, TenantRowView> tenantsById) {
        User invitee = usersById == null ? null : usersById.get(inv.getInviteeUserId());
        User inviter = inv.getInvitedBy() == null || usersById == null
                ? null : usersById.get(inv.getInvitedBy());
        TenantRowView tenant = tenantsById == null ? null : tenantsById.get(inv.getTenantId());
        return new TenantInvitationResponse(
                inv.getId(), inv.getTenantId(), tenant == null ? "" : tenant.getName(),
                inv.getInviteeUserId(), invitee == null ? "" : invitee.getEmail(),
                invitee == null ? "" : invitee.getUsername(), inv.getInvitedBy(),
                inviter == null ? "" : inviter.getEmail(),
                inviter == null ? "" : inviter.getUsername(),
                inv.getRole(), inv.getStatus(), inv.getMessage(), inv.getExpiresAt(),
                inv.getRespondedAt(), inv.getCreatedAt(), "",
                inv.getInviteeUserId() == null || inv.getInviteeUserId().isEmpty(),
                inv.getAcceptedCount());
    }

    public InvitationLookupView lookupView(String token) {
        try {
            TenantInvitation inv = invitationService.lookupByToken(token);
            return new InvitationLookupView(
                    inv.getTenantId(), inv.getRole(), inv.getExpiresAt());
        } catch (TenantRbacException e) {
            throw new BizException(new AppError(ErrorCode.NOT_FOUND.value(),
                    "invitation link is invalid or has been revoked", null, 410));
        }
    }

    public TenantInvitationResponse createInvitationResponse(long tenantId, User user,
            TenantRole role, String invitedBy, String message) {
        TenantInvitation inv = invitationService.create(
                tenantId, user.getId(), role, invitedBy, message);
        Map<String, User> usersById = new HashMap<>();
        usersById.put(user.getId(), user);
        if (invitedBy != null) {
            User inviter = userService.getUserById(invitedBy);
            if (inviter != null) {
                usersById.put(inviter.getId(), inviter);
            }
        }
        return projectInvitation(inv, usersById, null);
    }

    public TenantInvitationResponse createShareLinkResponse(long tenantId, TenantRole role,
            String invitedBy, String message) {
        TenantInvitation inv;
        try {
            inv = invitationService.createShareLink(tenantId, role, invitedBy, message);
        } catch (TenantRbacException e) {
            if (e.kind() == TenantRbacException.Kind.API_KEY_CANNOT_ASSIGN_OWNER) {
                throw e.asForbidden();
            }
            throw new BizException(AppError.internal("failed to create share link")
                    .withDetails(e.getMessage()));
        }
        return projectInvitationWithLink(inv, hydrateUsers(List.of(inv)), null);
    }

    public TenantInvitationResponse projectInvitationWithLink(TenantInvitation inv,
            Map<String, User> usersById, Map<Long, TenantRowView> tenantsById) {
        TenantInvitationResponse base = projectInvitation(inv, usersById, tenantsById);
        String inviteUrl = "";
        if (TenantInvitationService.STATUS_PENDING.equals(inv.getStatus())
                && inv.getToken() != null && !inv.getToken().isEmpty()) {
            inviteUrl = frontendBaseUrl() + "/register?token=" + inv.getToken();
        }
        return new TenantInvitationResponse(base.id(), base.tenantId(), base.tenantName(),
                base.inviteeUserId(), base.inviteeEmail(), base.inviteeName(), base.invitedBy(),
                base.inviterEmail(), base.inviterName(), base.role(), base.status(), base.message(),
                base.expiresAt(), base.respondedAt(), base.createdAt(), inviteUrl,
                base.isShareLink(), base.acceptedCount());
    }

    public String frontendBaseUrl() {
        String candidate = environment.getProperty(FRONTEND_BASE_URL_PROPERTY, "");
        String out = candidate == null ? "" : candidate.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public Map<String, User> hydrateUsers(List<TenantInvitation> invs) {
        Map<String, User> out = new HashMap<>();
        if (invs == null || invs.isEmpty()) {
            return out;
        }
        Set<String> ids = new java.util.HashSet<>();
        for (TenantInvitation inv : invs) {
            if (inv.getInviteeUserId() != null && !inv.getInviteeUserId().isEmpty()) {
                ids.add(inv.getInviteeUserId());
            }
            if (inv.getInvitedBy() != null) {
                ids.add(inv.getInvitedBy());
            }
        }
        try {
            return userService.getUsersByIds(ids);
        } catch (RuntimeException e) {
            return out;
        }
    }

    public Map<Long, TenantRowView> hydrateTenants(List<TenantInvitation> invs) {
        Map<Long, TenantRowView> out = new HashMap<>();
        if (invs == null || invs.isEmpty()) {
            return out;
        }
        Set<Long> ids = new java.util.HashSet<>();
        for (TenantInvitation inv : invs) {
            ids.add(inv.getTenantId());
        }
        try {
            return tenantService.getTenantsByIds(ids);
        } catch (RuntimeException e) {
            return out;
        }
    }

    public static Map<String, Object> membershipResponse(TenantMember member, String tenantName) {
        Map<String, Object> membership = new LinkedHashMap<>();
        membership.put("joinedAt", member.getJoinedAt());
        membership.put("role", member.getRole());
        membership.put("status", member.getStatus());
        membership.put("tenantId", member.getTenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("membership", membership);
        body.put("tenantName", tenantName == null ? "" : tenantName);
        return body;
    }
}
