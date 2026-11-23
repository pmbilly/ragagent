package com.ragagent.auth.controller.vo;

/**
 * {@code GET /auth/config} 的 HTTP 响应体（公共读）。
 *
 * <p>{@code edition} 是部署版本信号，前端据此决定是否尝试 {@code /auth/auto-setup}。</p>
 */
public record AuthConfigRespVo(boolean complexPasswordEnabled, String registrationMode, String edition) {
}
