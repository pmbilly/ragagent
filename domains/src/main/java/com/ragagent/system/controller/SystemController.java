package com.ragagent.system.controller;

import com.ragagent.contract.doc.DocReaderAdminPort;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.storage.StorageConnectivityPort;
import com.ragagent.contract.tenant.TenantConfigLookup;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.security.SsrfGuard;
import com.ragagent.platform.web.ApiResult;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.system.dto.SystemDtos;
import com.ragagent.system.service.DeploymentCapabilitiesHolder;
import com.ragagent.system.service.ParserEngineRegistry;
import com.ragagent.system.service.SystemInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * /api/v1/system 组：读端（capabilities/info/parser-engines/storage-engine-status）Viewer+；
 * 探测端（parser-engines/check、docreader/reconnect、storage-engine-check）Admin+——
 * 它们拿租户凭据主动探测远端服务。
 *
 * <p>响应一律为裸资源对象（无 {@code code/data/msg} 信封）；错误走 AppError 信封，
 * docreader 连接失败 → 503、被禁用的存储引擎 → 403。
 * parser/storage 的请求键名保持 snake（与租户配置 jsonb 同形，待解冻后
 * 再 DTO 化）。</p>
 *
 * <p><b>POST /system/sandbox-check 未实现</b>（依赖 sandbox 能力）——
 * Java 侧不映射 → Spring 404。RBAC 与 API-Key 策略表的同名死登记已删除
 * （落地 handler 时一并恢复）。</p>
 */
@RestController
@ApiResult
@RequestMapping("/api/v1/system")
@Tag(name = "System")
public class SystemController {

    private final SystemInfoService infoService;
    private final ParserEngineRegistry parserEngines;
    private final TenantConfigLookup tenantConfigLookup;
    private final DocReaderAdminPort docReader;
    private final SsrfGuard ssrfGuard;
    private final DeploymentCapabilitiesHolder capabilitiesHolder;
    private final StorageConnectivityPort storageProbe;

    public SystemController(SystemInfoService infoService,
                            ParserEngineRegistry parserEngines,
                            TenantConfigLookup tenantConfigLookup,
                            DocReaderAdminPort docReader,
                            SsrfGuard ssrfGuard,
                            DeploymentCapabilitiesHolder capabilitiesHolder,
                            StorageConnectivityPort storageProbe) {
        this.infoService = infoService;
        this.parserEngines = parserEngines;
        this.tenantConfigLookup = tenantConfigLookup;
        this.docReader = docReader;
        this.ssrfGuard = ssrfGuard;
        this.capabilitiesHolder = capabilitiesHolder;
        this.storageProbe = storageProbe;
    }

    // ── GET /capabilities ─────────────────────────────────────────────────

    /**
     * 启动快照 + 运行时 docker 覆盖
     * （overlayLiveDockerSandboxCapability）。Java 无 docker 后端 → docker 键恒按
     * sandbox=false 推导（route_not_registered）。
     */
    @GetMapping("/capabilities")
    public ResponseEntity<SystemDtos.DeploymentCapabilitiesData> capabilities() {
        return ResponseEntity.ok(capabilitiesHolder.snapshot());
    }


    // ── GET /info ─────────────────────────────────────────────────────────

    @GetMapping("/info")
    public ResponseEntity<SystemDtos.SystemInfoResponse> info() {
        long tenantId = currentTenantId();
        boolean minioEnabled = storageProbe.isMinioConfigured(tenantId);
        String dbVersion = infoService.dbVersion();
        SystemDtos.SystemInfoResponse response = new SystemDtos.SystemInfoResponse(
                infoService.getVersion(),
                infoService.getEdition(),
                infoService.getCommitId(),
                infoService.getBuildTime(),
                infoService.getJavaVersion(),
                infoService.keywordIndexEngine(),
                infoService.vectorStoreEngine(),
                infoService.graphDatabaseEngine(),
                minioEnabled,
                dbVersion,
                null,
                infoService.startedAt(),
                infoService.uptimeSeconds());
        return ResponseEntity.ok(response);
    }

    // ── GET /parser-engines ───────────────────────────────────────────────

    @GetMapping("/parser-engines")
    public ResponseEntity<SystemDtos.ParserEnginesResponse> listParserEngines() {
        return ResponseEntity.ok(engineListBody(engineOverrides()));
    }

    // ── POST /parser-engines/check ────────────────────────────────────────

    /**
     * 请求体 = ParserEngineConfig（未保存的表单值）。
     *
     * <p>请求键名保持 snake：与租户配置 jsonb 同形，待边界解冻后再 DTO 化。</p>
     */
    @PostMapping("/parser-engines/check")
    public ResponseEntity<SystemDtos.ParserEnginesResponse> checkParserEngines(
            @RequestBody(required = false) String rawBody) {
        JsonNode body = parseBody(rawBody);
        if (body == null) {
            throw new BizException(AppError.badRequest("请求参数不合法").withDetails("请求体格式不正确"));
        }
        Map<String, String> overrides = overridesFromRaw(body);
        return ResponseEntity.ok(engineListBody(overrides));
    }

    // ── POST /docreader/reconnect ─────────────────────────────────────────

    @PostMapping("/docreader/reconnect")
    public ResponseEntity<SystemDtos.ParserEnginesResponse> reconnectDocReader(
            @RequestBody(required = false) String rawBody) {
        JsonNode body = parseBody(rawBody);
        String addr = body == null || !body.has("addr") ? null : body.path("addr").asString(null);
        if (addr == null) {
            throw new BizException(AppError.badRequest("请提供 addr 参数"));
        }
        addr = addr.trim();
        if (addr.isEmpty()) {
            throw new BizException(AppError.badRequest("addr 不能为空"));
        }
        // SSRF 校验（DocReader 地址）
        try {
            ssrfGuard.validateURLForSSRF(addr);
        } catch (RuntimeException e) {
            throw new BizException(AppError.badRequest(
                    ssrfGuard.formatSSRFError("DocReader 地址", addr, e)));
        }
        try {
            docReader.reconnect(addr);
        } catch (RuntimeException e) {
            // 下游 docreader 不可达：503（错误语义化）
            throw new BizException(AppError.serviceUnavailable("连接失败: " + e.getMessage()));
        }
        List<SystemDtos.ParserEngineInfo> engines = parserEngines.listAllEngines(
                true, engineOverrides(),
                parserEngines.fetchRemoteEngines(true, engineOverrides()));
        return ResponseEntity.ok(new SystemDtos.ParserEnginesResponse(
                true, addr, docReader.config().transport(), engines));
    }

    // ── GET /storage-engine-status ────────────────────────────────────────

    @GetMapping("/storage-engine-status")
    public ResponseEntity<SystemDtos.StorageEngineStatusResponse> storageEngineStatus() {
        var status = storageProbe.status(currentTenantId());
        List<SystemDtos.StorageEngineStatusItem> engines = status.engines().stream()
                .map(e -> new SystemDtos.StorageEngineStatusItem(
                        e.provider(), e.allowed(), e.configured(), e.description()))
                .toList();
        return ResponseEntity.ok(new SystemDtos.StorageEngineStatusResponse(
                engines, status.allowedProviders(), status.minioEnvAvailable()));
    }

    // ── POST /storage-engine-check ────────────────────────────────────────

    /**
     * 连通性检测。
     *
     * <p>请求体 = 存储配置表单（键名 snake，与前端的存储设置一致；该配置最终落租户
     * jsonb，与上一致地待解冻后统一 DTO 化）。</p>
     */
    @PostMapping("/storage-engine-check")
    public ResponseEntity<SystemDtos.StorageCheckResponse> storageEngineCheck(
            @RequestBody(required = false) String rawBody) {
        JsonNode body = parseBody(rawBody);
        if (body == null) {
            throw new BizException(AppError.badRequest("请求参数不合法").withDetails("请求体格式不正确"));
        }
        String provider = body.path("provider").asString("");
        if (!storageProbe.isAllowed(provider)) {
            throw new BizException(AppError.forbidden("该存储引擎已被禁用"));
        }
        JsonNode cfg = body.get(provider);
        var outcome = storageProbe.check(provider, configMap(cfg), currentTenantId());
        return ResponseEntity.ok(
                new SystemDtos.StorageCheckResponse(outcome.ok(), outcome.message(), false));
    }

    // ── 租户/引擎上下文 ───────────────────────────────────────────────────

    /** ShouldBindJSON 等价：解析失败（含 EOF）→ null，调用方落各自的 400 文案。 */
    /** 契约端口只收纯 JDK 值：对象节点转 Map，其余视为缺失。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> configMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        Object value = JsonMappers.toJavaValue(node);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static JsonNode parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonMappers.lenient().readTree(rawBody);
            return node == null || !node.isObject() ? null : node;
        } catch (Exception e) {
            return null;
        }
    }

    private long currentTenantId() {
        Long tid = TenantContext.currentTenantId();
        return tid == null ? 0 : tid;
    }

    /** overrides 组装：租户 ParserEngineConfig → map。 */
    private Map<String, String> engineOverrides() {
        Map<String, String> overrides = new LinkedHashMap<>();
        JsonNode parserCfg = JsonMappers.toJsonNode(tenantConfigLookup.parserEngineConfig(currentTenantId()));
        if (parserCfg != null) {
            overrides.putAll(overridesFromRaw(parserCfg));
        }
        return overrides;
    }

    /** 从 jsonb 按固定键集提取解析引擎覆盖项。 */
    static Map<String, String> overridesFromRaw(JsonNode node) {
        Map<String, String> overrides = new LinkedHashMap<>();
        if (node == null || node.isNull() || !node.isObject()) {
            return overrides;
        }
        putString(overrides, node, "mineru_endpoint");
        putString(overrides, node, "mineru_api_key");
        putString(overrides, node, "mineru_model");
        putString(overrides, node, "mineru_vlm_server_url");
        putBool(overrides, node, "mineru_enable_formula");
        putBool(overrides, node, "mineru_enable_table");
        // mineru_parse_method：显式值归一；否则 legacy enable_ocr 推导
        String method = node.path("mineru_parse_method").asString("").trim().toLowerCase();
        JsonNode legacyOcr = node.get("mineru_enable_ocr");
        String resolved = switch (method) {
            case "auto" -> "auto";
            case "ocr" -> "ocr";
            case "txt" -> "txt";
            default -> legacyOcr != null && legacyOcr.isBoolean() && !legacyOcr.asBoolean() ? "txt" : "auto";
        };
        if (!node.path("mineru_parse_method").asString("").isEmpty() || (legacyOcr != null && legacyOcr.isBoolean())) {
            overrides.put("mineru_parse_method", resolved);
        }
        putBool(overrides, node, "mineru_enable_ocr");
        putString(overrides, node, "mineru_language");
        putString(overrides, node, "mineru_cloud_model");
        putBool(overrides, node, "mineru_cloud_enable_formula");
        putBool(overrides, node, "mineru_cloud_enable_table");
        putBool(overrides, node, "mineru_cloud_enable_ocr");
        putString(overrides, node, "mineru_cloud_language");
        putString(overrides, node, "odl_hybrid");
        putString(overrides, node, "odl_hybrid_url");
        putString(overrides, node, "odl_hybrid_mode");
        putBool(overrides, node, "odl_hybrid_fallback");
        putBool(overrides, node, "odl_markdown_with_html");
        putString(overrides, node, "paddleocr_vl_endpoint");
        putBool(overrides, node, "paddleocr_vl_use_seal_recognition");
        putBool(overrides, node, "paddleocr_vl_use_chart_recognition");
        putString(overrides, node, "paddleocr_vl_cloud_token");
        putString(overrides, node, "paddleocr_vl_cloud_model");
        putBool(overrides, node, "paddleocr_vl_cloud_use_seal_recognition");
        putBool(overrides, node, "paddleocr_vl_cloud_use_chart_recognition");
        return overrides;
    }

    private static void putString(Map<String, String> m, JsonNode node, String field) {
        String v = node.path(field).asString("");
        if (!v.isEmpty()) {
            m.put(field, v);
        }
    }

    private static void putBool(Map<String, String> m, JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v != null && v.isBoolean()) {
            m.put(field, String.valueOf(v.asBoolean()));
        }
    }

    // ── 响应组装辅助 ──────────────────────────────────────────────────────

    /** parser-engines 响应组装（连接态 + docreader 信息 + 引擎清单）。 */
    private SystemDtos.ParserEnginesResponse engineListBody(Map<String, String> overrides) {
        boolean connected = docReader.isConnected();
        List<SystemDtos.ParserEngineInfo> remote =
                parserEngines.fetchRemoteEngines(connected, overrides);
        List<SystemDtos.ParserEngineInfo> engines =
                parserEngines.listAllEngines(connected, overrides, remote);
        return new SystemDtos.ParserEnginesResponse(
                connected, docReader.config().addr(), docReader.config().transport(), engines);
    }

}
