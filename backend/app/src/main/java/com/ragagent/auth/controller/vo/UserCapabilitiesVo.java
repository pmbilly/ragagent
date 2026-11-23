package com.ragagent.auth.controller.vo;

/** {@code GET /auth/me} 的能力位：能否自助建空间、是否自动接受邀请。 */
public record UserCapabilitiesVo(boolean autoAcceptInvitation, boolean canCreateTenant) {
}
