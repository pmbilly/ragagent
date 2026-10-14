package com.ragagent.common.storage.fileserve;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件代理路由的能力端口（L1）：把"按作用域直出文件"的两条既有 handler 体
 * 收成端口，供知识库、会话消息、embed 渠道等跨域路由复用，而不直连存储域实现。
 *
 * <p><b>两条能力</b>：</p>
 * <ul>
 *   <li>{@link #serveTenantFiles} —— 租户级 {@code /files} 同 handler 体；</li>
 *   <li>{@link #serveKbFile} —— 知识库作用域：解析 {@code file_path}（缺参/非法 ⇒ 400）、
 *       按 KB 授权解析（不可达 ⇒ 404/403 信封）、命中后直出。KB 自身的访问闸门
 *       （{@code ChunkAccessGuard}）由调用方先过，本方法只吃它的产物（属主租户）。</li>
 * </ul>
 *
 * <p>响应写出小件见 {@link FileServeHttp}（同层，供调用方在自家错误分支复用）。</p>
 */
public interface FileProxyPort {

    /** 租户级文件服务（与 {@code GET /files} 同一 handler 体）。 */
    void serveTenantFiles(HttpServletRequest request, HttpServletResponse response) throws IOException;

    /**
     * 知识库作用域文件服务。
     *
     * @param kbOwnerTenantId KB 属主租户（调用方已过 KB 访问闸门；可为 {@code null}）
     * @param kbId            知识库 id（与路由路径变量一致）
     */
    void serveKbFile(Long kbOwnerTenantId, String kbId,
                     HttpServletRequest request, HttpServletResponse response) throws IOException;
}
