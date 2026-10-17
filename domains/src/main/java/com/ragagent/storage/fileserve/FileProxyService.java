package com.ragagent.storage.fileserve;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.storage.fileserve.FileContentService;
import com.ragagent.contract.storage.fileserve.MessageFactsLookup;
import com.ragagent.contract.storage.fileserve.OpenedFile;
import com.ragagent.contract.tenant.TenantConfigLookup;
import com.ragagent.contract.tenant.TenantConfigLookup.TenantStorageView;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.storage.fileserve.FileProxyPort;
import com.ragagent.platform.storage.fileserve.FileServeHttp;
import com.ragagent.platform.storage.fileserve.FileTransport;
import com.ragagent.platform.storage.fileserve.StoragePaths;
import com.ragagent.platform.text.ContentTypeByFilename;
import com.ragagent.storage.domain.StoredResource;
import com.ragagent.storage.service.ResourceCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文件代理面共享处理器：
 *
 * <ul>
 *   <li>{@code newFileServeHandler} → {@code serveTenantFiles}（GET /files，
 *       租户级存储代理，API-Key 守卫在路由自带——{@code allowFileServeApiKey}）；</li>
 *   <li>{@code presignedFileHandler} → {@code servePresigned}
 *       （GET+HEAD /api/v1/files/presigned，HMAC 免鉴权，AuthFilter noAuthAPI 放行）；</li>
 *   <li>{@code servePresignedPreview} 的 handler 体 → {@link #presignedPreview}
 *       （Admin 诊断；API-Key 主体在控制器里先行拒绝）；</li>
 *   <li>{@code serveResourceGrants} 的 handler 体 → {@link #serveResourceGrant}
 *       （GET+HEAD /r/{token}，无鉴权——AuthFilter 对 /r/ 前缀放行）；</li>
 *   <li>{@code serveAuthorizedFile} → {@link #serveAuthorizedFile}（KB/消息 scoped
 *       共用的落盘出口，Cache-Control private, no-store）。</li>
 * </ul>
 *
 * <p>无 body 的 4xx 状态响应用 {@link #plainStatus}；错误信封
 * {@code {"error": msg}} 用 {@link #writeErrorJson}（单键，无键序问题）；
 * presigned-preview 的多键 JSON 对象体按<b>字母序</b>插入 LinkedHashMap。</p>
 */
@Service
public class FileProxyService implements FileProxyPort {

    private static final Logger log = LoggerFactory.getLogger(FileProxyService.class);

    private final TenantConfigLookup tenantConfigLookup;
    private final StorageFileResolver resolver;
    private final ResourceCatalogService catalog;
    private final FileContentService globalFileService;
    private final FileAccessResolver accessResolver;

    /** 本地存储根目录（{@code LOCAL_STORAGE_BASE_DIR}，经 Spring 属性带 env 缺省，测试期可注入）。 */
    private final String localBaseDir;
    private final String absDir;

    public FileProxyService(TenantConfigLookup tenantConfigLookup, StorageFileResolver resolver,
            ResourceCatalogService catalog,
            FileAccessResolver accessResolver,
            @org.springframework.beans.factory.annotation.Value(
                    "${weknora.storage.local-base-dir:${LOCAL_STORAGE_BASE_DIR:${user.home}/ragagent-data/files}}")
            String localBaseDir) {
        this.tenantConfigLookup = tenantConfigLookup;
        this.resolver = resolver;
        this.catalog = catalog;
        this.localBaseDir = localBaseDir;
        this.absDir = java.nio.file.Path.of(localBaseDir).toAbsolutePath().normalize().toString();
        this.globalFileService = resolver.globalFileService(localBaseDir);
        this.accessResolver = accessResolver;
    }

    /** 进程级默认文件服务（沙箱附件 staging 等共享消费方读取；恒 local 基座+装饰视图）。 */
    public FileContentService globalFileService() {
        return globalFileService;
    }

    // ── 共享小件 ────────────────────────────────────────────────────────────

    /**
     * file_path 必填（trim 后）、禁 ".."。
     * 失败时 400 已写、返回 null。
     */
    public static String requireFilePathQuery(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return FileServeHttp.requireFilePathQuery(request, response);
    }

    /**
     * AllowFileServeAPIKey 守卫已由既有 {@code AllowFileServeAPIKeyInterceptor}
     * 承担（WebConfig 对 /files 与 KB 代理路由接线）——语义逐字一致。
     */

    // ── GET /files ──────────────────────────────────────────────────────────

    public void serveTenantFiles(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filePath = requireFilePathQuery(request, response);
        if (filePath == null) {
            return;
        }
        Long ctxTenantId = TenantContext.currentTenantId();
        if (ctxTenantId == null || ctxTenantId == 0) {
            writeErrorJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized: workspace context missing");
            return;
        }
        TenantStorageView tenant = tenantConfigLookup.storageView(ctxTenantId);
        if (tenant == null) {
            writeErrorJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized: workspace context missing");
            return;
        }

        // resolveCatalogResource：resource:// 引用的租户是权威（物理 provider 路径
        // 不要求编码访问控制元数据）
        String resolved = filePath;
        boolean resourceResolved = false;
        ResourceCatalogService.ResolvedPath catalogHit = catalog.resolvePath(filePath);
        if (catalogHit.error()) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (catalogHit.resource() != null) {
            StoredResource resource = catalogHit.resource();
            if (resource.getTenantId() != ctxTenantId) {
                writeErrorJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "forbidden: resource not accessible");
                return;
            }
            resolved = catalogHit.physicalPath();
            resourceResolved = true;
        }
        if (!resourceResolved) {
            String tenantError = StoragePaths.validateStoragePathTenantError(resolved, ctxTenantId);
            if (tenantError != null) {
                log.warn("[Router] /files denied cross-tenant or invalid path: tenant_id={} file_path=\"{}\" err={}",
                        ctxTenantId, resolved, tenantError);
                writeErrorJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "forbidden: file path not accessible");
                return;
            }
        }

        StoragePaths.StorageTarget target = StoragePaths.parseStorageTarget(resolved);
        StorageFileResolver.Resolution resolution = resolver.resolveTenantFileServiceWithFallback(
                "/files", tenant, target.backendId(), target.provider(),
                absDir, globalFileService);
        if (!resolution.ok()) {
            plainStatus(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        OpenedFile opened;
        try {
            opened = resolution.service().getFile(resolved);
        } catch (Exception e) {
            log.warn("[Router] /files get file failed: tenant_id={} provider={} path=\"{}\"", ctxTenantId, resolution.resolvedProvider(), resolved, e);
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var safe = ContentTypeByFilename.safe(resolved);
        streamStoredFile(response, request, opened, resolved, safe.contentType(), safe.inline(),
                "public, max-age=86400");
    }

    // ── GET+HEAD /api/v1/files/presigned ────────────────────────────────────

    /** GET/HEAD 共用。 */
    public void servePresigned(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filePath = trimToEmpty(request.getParameter("file_path"));
        String tenantIdStr = trimToEmpty(request.getParameter("tenant_id"));
        String expiresStr = trimToEmpty(request.getParameter("expires"));
        String sig = trimToEmpty(request.getParameter("sig"));

        if (filePath.isEmpty() || tenantIdStr.isEmpty() || expiresStr.isEmpty() || sig.isEmpty()) {
            writeErrorJson(response, HttpServletResponse.SC_BAD_REQUEST, "missing required parameters");
            return;
        }
        if (filePath.contains("..")) {
            writeErrorJson(response, HttpServletResponse.SC_BAD_REQUEST, "invalid file path");
            return;
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(tenantIdStr);
            if (tenantId < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            writeErrorJson(response, HttpServletResponse.SC_BAD_REQUEST, "invalid tenant_id");
            return;
        }
        if (!StoragePaths.verifyFileUrlSig(filePath, tenantId, expiresStr, sig)) {
            writeErrorJson(response, HttpServletResponse.SC_FORBIDDEN, "invalid or expired signature");
            return;
        }
        TenantStorageView tenant;
        try {
            tenant = tenantConfigLookup.storageView(tenantId);
        } catch (RuntimeException e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (tenant == null) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        StoragePaths.StorageTarget target = StoragePaths.parseStorageTarget(filePath);
        StorageFileResolver.Resolution resolution = resolver.resolveFileService(tenant,
                target.backendId(), target.provider(), absDir);
        if (!resolution.ok()) {
            plainStatus(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        OpenedFile opened;
        try {
            opened = resolution.service().getFile(filePath);
        } catch (Exception e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var safe = ContentTypeByFilename.safe(filePath);
        // ⚠️ 此处不带 filename——Content-Type 从路径派生，Content-Disposition 落成
        // 裸 "inline"/"attachment"。
        streamStoredFile(response, request, opened, "", safe.contentType(), safe.inline(),
                "public, max-age=86400");
    }

    // ── GET /api/v1/files/presigned-preview（Admin 诊断）────────────────────

    /**
     * API-Key 主体的拒绝
     * （DenyAPIKeyPrincipal）在控制器里先行；Admin 角色门由 RbacInterceptor 承担
     * （对 API-Key 主体短路，见其 apiKey 分支）。
     */
    public void presignedPreview(HttpServletRequest request, HttpServletResponse response,
            TenantStorageView tenant) throws IOException {
        String filePath = requireFilePathQuery(request, response);
        if (filePath == null) {
            return;
        }
        StoragePaths.StorageTarget target = StoragePaths.parseStorageTarget(filePath);
        StorageFileResolver.Resolution resolution = resolver.resolveFileService(tenant,
                target.backendId(), target.provider(), absDir);
        if (!resolution.ok()) {
            // JSON 键按字母序：error < hint < provider
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", resolution.error());
            body.put("hint", "workspace storage config is missing or incomplete for this provider");
            body.put("provider", target.provider());
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, body);
            return;
        }
        String httpUrl;
        try {
            httpUrl = resolution.service().getFileURL(filePath);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", String.valueOf(e.getMessage()));
            body.put("hint", "GetFileURL failed; for local storage this usually means APP_EXTERNAL_URL is unset");
            body.put("provider", resolution.resolvedProvider());
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, body);
            return;
        }
        boolean rewritten = !httpUrl.equals(filePath);
        String hint = "";
        if (!rewritten) {
            hint = "URL unchanged; for local storage set APP_EXTERNAL_URL to enable presigned HTTP URLs";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        // JSON 对象键按字母序输出：file_path < hint < provider
        // < rewritten < url
        body.put("file_path", filePath);
        body.put("hint", hint);
        body.put("provider", resolution.resolvedProvider());
        body.put("rewritten", rewritten);
        body.put("url", httpUrl);
        writeJson(response, HttpServletResponse.SC_OK, body);
    }

    // ── GET+HEAD /r/{token}（capability URL）────────────────────────────────

    /** 无鉴权，短时令牌自证。 */
    public void serveResourceGrant(String token, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        StoredResource resource = catalog.resolveAccessGrant(token).orElse(null);
        if (resource == null) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        TenantStorageView tenant;
        try {
            tenant = tenantConfigLookup.storageView(resource.getTenantId());
        } catch (RuntimeException e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (tenant == null) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // grant 行自带 backend ID；只有 provider scheme 来自物理路径
        StoragePaths.StorageTarget target = StoragePaths.parseStorageTarget(resource.getPhysicalPath());
        StorageFileResolver.Resolution resolution = resolver.resolveFileService(tenant,
                resource.getStorageBackendId(), target.provider(), localBaseDir);
        if (!resolution.ok() || resolution.service() == null) {
            log.warn("[Router] resource grant storage resolution failed: resource_id={} err={}",
                    resource.getId(), resolution.error());
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        OpenedFile opened;
        try {
            opened = resolution.service().getFile(resource.getPhysicalPath());
        } catch (Exception e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String fileName = resource.getOriginalName() == null || resource.getOriginalName().isEmpty()
                ? resource.getPhysicalPath() : resource.getOriginalName();
        var safe = ContentTypeByFilename.safe(fileName);
        streamStoredFile(response, request, opened, fileName, safe.contentType(), safe.inline(),
                "private, max-age=300");
    }

    // ── KB / 消息 scoped 共用出口 ───────────────────────────────────────────

    /**
     * 存储消费授权后的定位符；租户查不到 → 404、
     * 解析失败 → 400、对象缺失 → 404；成功以 private, no-store 直出
     * （Content-Type/inline 由 filename 在 Serve 内派生，无 ContentType 选项）。
     */
    /**
     * 知识库作用域 handler 体（自 {@code KnowledgeBaseFileProxyController.files} 原样搬入）：
     * 解析 {@code file_path} → 按 KB 授权解析 → 错误信封 → 直出。
     * KB 访问闸门（{@code ChunkAccessGuard}）由调用方先过，本方法只吃其产物（属主租户）。
     */
    @Override
    public void serveKbFile(Long kbOwnerTenantId, String kbId,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String reference = requireFilePathQuery(request, response);
        if (reference == null) {
            return;
        }
        FileAccess file;
        try {
            file = accessResolver.resolveKbFile(kbOwnerTenantId, kbId, reference);
        } catch (FileAccessException e) {
            fileAccessError(response, e);
            return;
        } catch (BizException e) {
            // IsAppError 支路：{"error": message}（非全局信封）
            writeErrorJson(response, e.appError().httpCode(), e.appError().message());
            return;
        }
        serveAuthorizedFile(response, request, file, "KB files");
    }

    /**
     * 会话消息作用域 handler 体（自 {@code MessageFileProxyController.files} 原样搬入）：
     * 解析 {@code file_path} → 消息授权匹配（事实由回调现取）→ 错误信封 → 直出。
     */
    @Override
    public void serveMessageFile(String sessionId, String messageId, MessageFactsLookup messages,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String reference = requireFilePathQuery(request, response);
        if (reference == null) {
            return;
        }
        FileAccess file;
        try {
            file = accessResolver.resolveMessageFile(sessionId, messageId, reference, messages);
        } catch (FileAccessException e) {
            fileAccessError(response, e);
            return;
        }
        serveAuthorizedFile(response, request, file, "message files");
    }

    public void serveAuthorizedFile(HttpServletResponse response, HttpServletRequest request,
            FileAccess file, String tag) throws IOException {
        TenantStorageView tenant;
        try {
            tenant = tenantConfigLookup.storageView(file.ownerTenantId());
        } catch (RuntimeException e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (tenant == null) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        StoragePaths.StorageTarget target = StoragePaths.parseStorageTarget(file.path());
        String backendId = file.storageBackendId() == null || file.storageBackendId().isEmpty()
                ? target.backendId() : file.storageBackendId();
        StorageFileResolver.Resolution resolution = resolver.resolveTenantFileServiceWithFallback(
                tag, tenant, backendId, target.provider(), absDir,
                globalFileService);
        if (!resolution.ok()) {
            plainStatus(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        OpenedFile opened;
        try {
            opened = resolution.service().getFile(file.path());
        } catch (Exception e) {
            plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        FileTransport.serve(response, request, opened,
                new FileTransport.Options(file.filename(), false, "", "", "private, no-store", 0));
    }

    /** 授权三态的响应映射；true=已写响应。 */
    public static boolean fileAccessError(HttpServletResponse response, FileAccessException e)
            throws IOException {
        switch (e.kind()) {
            case NOT_FOUND -> plainStatus(response, HttpServletResponse.SC_NOT_FOUND);
            case UNAUTHORIZED -> writeErrorJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized: workspace context missing");
            case FORBIDDEN -> writeErrorJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "forbidden: file not accessible from this resource");
        }
        return true;
    }

    // ── 响应写出小件 ────────────────────────────────────────────────────────

    /** 按派生参数直出已打开的存储对象。 */
    private static void streamStoredFile(HttpServletResponse response, HttpServletRequest request,
            OpenedFile opened, String filename, String contentType, boolean inline,
            String cacheControl) throws IOException {
        FileTransport.serve(response, request, opened,
                new FileTransport.Options(filename, !inline, contentType, "", cacheControl, 0));
    }

    /**
     * 只写状态码（**空体**，无 Content-Type）。
     * ⚠️ 只 setStatus 不够：Tomcat 的 ErrorReportValve 会在响应未提交且状态 ≥400 时
     * 补默认错误体（"404 Not Found" 字样，Spring Boot 的 showReport=false 形态）。
     * setContentLength(0)+flush 提交空响应后阀门跳过，保证响应体为空。
     */
    public static void plainStatus(HttpServletResponse response, int status) {
        FileServeHttp.plainStatus(response, status);
    }

    /** 错误信封：{@code {"error": msg}}。 */
    public static void writeErrorJson(HttpServletResponse response, int status, String message)
            throws IOException {
        FileServeHttp.writeErrorJson(response, status, message);
    }

    /**
     * 响应写出：Content-Type {@code application/json; charset=utf-8}（带空格，
     * setHeader 原样写——同既有的容器经验），JSON 键按字母序
     * （调用方保证）。体走 UTF-8 字节（getWriter 会受容器默认编码影响）。
     */
    public static void writeJson(HttpServletResponse response, int status, Object body)
            throws IOException {
        FileServeHttp.writeJson(response, status, body);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
