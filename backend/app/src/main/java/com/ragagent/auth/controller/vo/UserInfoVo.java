package com.ragagent.auth.controller.vo;

import com.ragagent.auth.api.UserPreferences;
import java.time.OffsetDateTime;

/**
 * 用户信息的 HTTP 响应体。
 *
 * <p>与实体序列化的差别：<b>没有 deletedAt</b>；avatar、tenantId、preferences 恒输出。
 * {@code preferences} 是跨域 api 形状（{@code auth/api}），按判据直出。</p>
 */
public record UserInfoVo(
        String id,
        String username,
        String email,
        String avatar,
        long tenantId,
        boolean isActive,
        boolean canAccessAllTenants,
        boolean isSystemAdmin,
        UserPreferences preferences,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
