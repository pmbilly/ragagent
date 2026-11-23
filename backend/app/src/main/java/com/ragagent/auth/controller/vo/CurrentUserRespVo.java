package com.ragagent.auth.controller.vo;

import java.util.List;
import java.util.Map;

/** {@code GET /auth/me}：当前用户视图（活动空间、能力位、成员关系与偏好默认值）。 */
public record CurrentUserRespVo(
        UserCapabilitiesVo capabilities,
        List<MembershipVo> memberships,
        Map<String, Object> preferenceDefaults,
        TenantRespVo tenant,
        boolean tenantRequired,
        UserInfoVo user) {
}
