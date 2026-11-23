package com.ragagent.auth.controller.vo;

import com.ragagent.auth.domain.User;
import java.util.List;

/**
 * 登录 / 自动设置 / 切换空间的 HTTP 响应体。
 *
 * <p>{@code user} 的线格式就是实体投影（含 deletedAt 等字段），换形状会改变响应字节，
 * 因此保持实体类型；登录失败不走本类型（统一 AppError 信封）。</p>
 */
public record AuthLoginRespVo(
        User user,
        TenantRespVo activeTenant,
        List<MembershipVo> memberships,
        String token,
        String refreshToken) {
}
