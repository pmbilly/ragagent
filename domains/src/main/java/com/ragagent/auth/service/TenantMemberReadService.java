package com.ragagent.auth.service;

import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.TenantMemberResponse;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.pagination.PageResult;
import com.ragagent.contract.tenant.TenantRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 成员控制面读模型：列表投影与直加/邀请共用的 201 响应。 */
@Service
public class TenantMemberReadService {

    private final TenantMemberService memberService;
    private final UserService userService;

    public TenantMemberReadService(TenantMemberService memberService, UserService userService) {
        this.memberService = memberService;
        this.userService = userService;
    }

    public Map<String, Object> listMembers(long tenantId, String q, int page, int pageSize) {
        PageResult<TenantMember> rows =
                memberService.listMembersPage(tenantId, q, page, pageSize);
        List<String> ids = new ArrayList<>();
        for (TenantMember m : rows.items()) {
            ids.add(m.getUserId());
        }
        Map<String, User> usersById = userService.getUsersByIds(ids);
        List<TenantMemberResponse> resp = new ArrayList<>(rows.items().size());
        for (TenantMember m : rows.items()) {
            resp.add(memberResponse(m, usersById.get(m.getUserId())));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("members", resp);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("total", rows.total());
        return data;
    }

    public PreparedResponse addByEmail(String email, long tenantId, TenantRole role,
            String invitedBy) {
        User user = userService.getUserByEmail(email);
        if (user == null) {
            throw new BizException(AppError.notFound(
                    "user with this email is not registered; ask them to sign up first"));
        }
        return addMemberAndRespond(user.getId(), user.getEmail(), user.getUsername(),
                user.getAvatar(), tenantId, role, invitedBy);
    }

    public PreparedResponse addMemberAndRespond(String userId, String email, String username,
            String avatar, long tenantId, TenantRole role, String invitedBy) {
        try {
            TenantMember member = memberService.addMemberChecked(
                    userId, tenantId, role, invitedBy);
            return new PreparedResponse(new TenantMemberResponse(
                    member.getUserId(),
                    email == null ? "" : email,
                    username == null ? "" : username,
                    avatar == null ? "" : avatar,
                    member.getRole(),
                    member.getStatus(),
                    member.getInvitedBy(),
                    member.getJoinedAt()), 201);
        } catch (TenantRbacException e) {
            switch (e.kind()) {
                case INVALID_TENANT_ROLE -> throw e.asValidationError();
                case API_KEY_CANNOT_ASSIGN_OWNER -> throw e.asForbidden();
                case MEMBERSHIP_ALREADY_EXISTS -> throw e.asConflict();
                default -> throw new BizException(AppError.internal("failed to add member")
                        .withDetails(e.getMessage()));
            }
        }
    }

    private static TenantMemberResponse memberResponse(TenantMember member, User user) {
        return new TenantMemberResponse(
                member.getUserId(),
                user == null ? "" : user.getEmail(),
                user == null ? "" : user.getUsername(),
                user == null ? "" : user.getAvatar(),
                member.getRole(),
                member.getStatus(),
                member.getInvitedBy(),
                member.getJoinedAt());
    }

    public record PreparedResponse(TenantMemberResponse body, int status) {
    }
}
