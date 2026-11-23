package com.ragagent.auth.controller.vo;

/** 登录响应的空间成员投影：tenantId / tenantName / role。 */
public record MembershipVo(
        Long tenantId,
        String tenantName,
        String role) {
}
