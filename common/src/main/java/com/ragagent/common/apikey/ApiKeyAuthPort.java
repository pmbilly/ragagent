package com.ragagent.common.apikey;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * X-API-Key 认证通道口（B209 ✓）。
 *
 * <p><b>动机</b>：{@code auth/filter/AuthFilter} 原先直接依赖
 * {@code auth.apikey.filter.APIKeyAuthChannel} ✗ —— 这是 P4 前 <b>最后一条</b>
 * main 侧 {@code :domains → apikey} 边（见 {@code docs/phase4-module-boundaries-plan.md} §12 ✓）。</p>
 *
 * <p><b>契约</b>：鉴权成功时由实现**自行写入** principal / scope 上下文并返回 {@code true} ✓；
 * 失败时实现按既有口径写响应（401/403/400 ✓）并返回 {@code false} ✓。形态与
 * {@code APIKeyAuthChannel#authenticate} 逐字一致 ✓（该方法是原实现的公开面 ✓）。</p>
 */
public interface ApiKeyAuthPort {

    /** 尝试用 {@code X-API-Key} 认证；成功写上下文并返回 true ✓。 */
    boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException;
}
