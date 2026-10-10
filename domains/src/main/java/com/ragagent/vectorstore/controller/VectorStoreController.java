package com.ragagent.vectorstore.controller;

import com.ragagent.common.web.RequestFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ragagent.common.context.TenantContext;
import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;
import com.ragagent.common.web.ApiResponse;
import com.ragagent.common.web.ApiResult;
import com.ragagent.common.vectorstore.ConnectionConfig;
import com.ragagent.vectorstore.domain.EnvVectorStores;
import com.ragagent.common.vectorstore.IndexConfig;
import com.ragagent.vectorstore.domain.VectorStore;
import com.ragagent.vectorstore.dto.VectorStoreResponse;
import com.ragagent.vectorstore.dto.VectorStoreTypes;
import com.ragagent.vectorstore.service.VectorStoreConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量库管理端（8 条路由；读 Viewer+ / 写与测试 Admin+）。
 *
 * <p>env store（__env_* 前缀）是**路径判定先于存在性**：PUT/DELETE 对任何 __env_*
 * id 一律 400 readonly（即使该 id 不存在）；GET /test 则先查 env 表 → 404。
 * 本部署 RETRIEVE_DRIVER 未配置 → env 列表恒空（部署状态，golden 钉住）。</p>
 *
 * <p><b>响应形态（B185 起统一外壳 {@code {code,message,data}}，见 docs/api-response-convention.md）</b>：
 * 成功体一律外壳（列表/对象进 {@code data}；201 保留、204 退役 ⇒ 删除返 200 + 外壳）；
 * 错误体同形（{@code {code,message,data}}），**HTTP 状态码随语义**（404/400/401…）。</p>
 *
 * <p>本类此前有四处**私有错误形态**（{@code {"error":"…"}} 纯字符串 + test 失败返 **200**），
 * 已全部退役：改由 {@code BizException(AppError…)} 走全局处理器 ⇒ 与其余域同形。
 * 其中「test 失败返 200」是 Go 期产物 ✗（200 却带错误体，前端只能靠字符串嗅探）；
 * 现在服务端错误就是真错误状态（400），前端用 resolve/reject 判断 ⇒ 顺带修掉
 * {@code VectorStoreSettings.vue} 里「读 res.success 恒为 undefined、成功分支永不亮」的存量缺陷。</p>
 */
@RestController
@ApiResult
@RequestMapping("/api/v1/vector-stores")
public class VectorStoreController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VectorStoreConfigService service;

    public VectorStoreController(VectorStoreConfigService service) {
        this.service = service;
    }

    // ── 请求 DTO ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateStoreRequest(
            String name,
            String engineType,
            ConnectionConfig connectionConfig,
            IndexConfig indexConfig) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateStoreRequest(
            String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestStoreRequest(
            String engineType,
            ConnectionConfig connectionConfig) {}

    // ── 端点 ───────────────────────────────────────────────────────────

    @GetMapping("/types")
    public ResponseEntity<?> listStoreTypes() {
        return ResponseEntity.ok(VectorStoreTypes.all());
    }

    @PostMapping("/test")
    public ResponseEntity<?> testStoreRaw(@RequestBody(required = false) String rawBody) {
        requireTenant(); // 调用即鉴权（未认证会抛 TenantMissing）；返回值本方法不用
        TestStoreRequest req = bind(rawBody, TestStoreRequest.class);
        if (req.engineType() == null || req.engineType().isEmpty()) {
            throw validator("EngineType");
        }
        // ConnectionConfig 的必填校验不在这里做（嵌套结构校验行为不触发），
        // 直接落到引擎必填校验。
        // B185：不再包成 200 + 纯字符串错误体（Go 期形态）⇒ 让 BizException 带自己的码/状态出去
        String version = service.testRawConnection(req.engineType(), req.connectionConfig());
        return ResponseEntity.ok(versionBody(version));
    }

    @PostMapping
    public ResponseEntity<?> createStore(@RequestBody(required = false) String rawBody) {
        long tenantId = requireTenant();
        JsonNode body = parseOrValidator(rawBody);
        String name = textOrNull(body, "name");
        String engineType = textOrNull(body, "engineType");
        List<String> missing = new ArrayList<>();
        if (name == null) {
            missing.add("Name");
        }
        if (engineType == null) {
            missing.add("EngineType");
        }
        if (!missing.isEmpty()) {
            throw validator(missing.toArray(new String[0]));
        }
        VectorStore store = new VectorStore();
        store.setId(UUID.randomUUID().toString());
        store.setTenantId(tenantId);
        store.setName(name);
        store.setEngineType(engineType);
        store.setConnectionConfig(configOf(body, "connectionConfig"));
        store.setIndexConfig(indexOf(body, "indexConfig"));
        try {
            service.create(store);
        } catch (RuntimeException e) {
            throw e; // AppError 原样进信封
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VectorStoreResponse.of(store, "user", false));
    }

    /** env stores 在前、DB stores 在后（合并顺序固定） */
    @GetMapping
    public ResponseEntity<?> listStores() {
        long tenantId = requireTenant();
        List<VectorStoreResponse> all = new ArrayList<>();
        for (VectorStore env : service.envStores()) {
            all.add(VectorStoreResponse.of(env, "env", true));
        }
        for (VectorStore s : service.list(tenantId)) {
            all.add(VectorStoreResponse.of(s, "user", false));
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStore(@PathVariable("id") String id) {
        long tenantId = requireTenant();
        if (EnvVectorStores.isEnvStoreId(id)) {
            VectorStore env = service.findEnvStore(id);
            if (env == null) {
                throw notFoundPure();
            }
            return ResponseEntity.ok(VectorStoreResponse.of(env, "env", true));
        }
        VectorStore store = owned(tenantId, id);
        return ResponseEntity.ok(VectorStoreResponse.of(store, "user", false));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStore(@PathVariable("id") String id,
            @RequestBody(required = false) String rawBody) {
        long tenantId = requireTenant();
        if (EnvVectorStores.isEnvStoreId(id)) {
            throw readonlyEnv();
        }
        owned(tenantId, id);
        UpdateStoreRequest req = bind(rawBody, UpdateStoreRequest.class);
        if (req.name() == null || req.name().isEmpty()) {
            throw validator("Name");
        }
        VectorStore updated = new VectorStore();
        updated.setId(id);
        updated.setTenantId(tenantId);
        updated.setName(req.name());
        service.updateName(updated);
        VectorStore result = service.getByID(tenantId, id);
        if (result != null) {
            return ResponseEntity.ok(VectorStoreResponse.of(result, "user", false));
        }
        // owned() 已确认存在，理论不可达；防御分支按 404 走
        throw notFoundPure();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteStore(@PathVariable("id") String id) {
        long tenantId = requireTenant();
        if (EnvVectorStores.isEnvStoreId(id)) {
            throw readonlyEnv();
        }
        owned(tenantId, id);
        service.delete(tenantId, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testStoreByID(@PathVariable("id") String id) {
        long tenantId = requireTenant();
        if (EnvVectorStores.isEnvStoreId(id)) {
            VectorStore env = service.findEnvStore(id);
            if (env == null) {
                throw notFoundPure();
            }
            String version = service.testConnection(env.getEngineType(), env.getConnectionConfig());
            return ResponseEntity.ok(versionBody(version));
        }
        VectorStore store = owned(tenantId, id);
        String version = service.testConnection(store.getEngineType(), store.getConnectionConfig());
        // 探测到版本且与存量不同 → 回存（SaveDetectedVersion）
        if (!version.isEmpty() && !version.equals(store.getConnectionConfig().version)) {
            try {
                store.getConnectionConfig().version = version;
                service.saveDetectedVersion(store);
            } catch (RuntimeException e) {
                // 仅记 WARN 后继续（不影响响应）
            }
        }
        return ResponseEntity.ok(versionBody(version));
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    private VectorStore owned(long tenantId, String id) {
        VectorStore store;
        try {
            store = service.getByID(tenantId, id);
        } catch (RuntimeException e) {
            throw BizException.internal("failed to query vector store");
        }
        if (store == null) {
            throw notFoundPure();
        }
        return store;
    }

    private static ConnectionConfig configOf(JsonNode body, String field) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) {
            return new ConnectionConfig();
        }
        try {
            return MAPPER.treeToValue(n, ConnectionConfig.class);
        } catch (Exception e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private static IndexConfig indexOf(JsonNode body, String field) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) {
            return new IndexConfig();
        }
        try {
            return MAPPER.treeToValue(n, IndexConfig.class);
        } catch (Exception e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private static String textOrNull(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static JsonNode parseOrValidator(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            throw BizException.badRequest("No content to map due to end-of-input");
        }
        try {
            return MAPPER.readTree(rawBody);
        } catch (Exception e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private static long requireTenant() {
        Long tenantId = TenantContext.currentTenantId();
        if (tenantId == null || tenantId == 0) {
            throw BizException.unauthorized("unauthorized: workspace context missing");
        }
        return tenantId;
    }

    /** env store 只读 → 400（B185：原为私有 ReadonlyEnvStore + 纯字符串体）。 */
    private static BizException readonlyEnv() {
        return BizException.badRequest("environment-configured vector stores cannot be modified via API");
    }

    private static BizException validator(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(RequestFields.message(field, "required"));
        }
        return BizException.badRequest(sb.toString());
    }

    private static <T> T bind(String rawBody, Class<T> type) {
        if (rawBody == null || rawBody.isEmpty()) {
            throw BizException.badRequest("No content to map due to end-of-input");
        }
        try {
            return MAPPER.readValue(rawBody, type);
        } catch (Exception e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /** 404 → BizException(AppError.notFound)（B185：原为私有 PureNotFound + 纯字符串体）。 */
    private static BizException notFoundPure() {
        return new BizException(AppError.notFound("vector store not found"));
    }

    /** 连通性测试成功体：{version}（探测不到为空串照写）。 */
    private static Map<String, Object> versionBody(String version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version == null ? "" : version);
        return body;
    }


    // B185：本类原有的四处私有错误形态（{"error":"…"} 纯字符串 + test 失败 200）已退役 ——
    // 统一走 BizException(AppError…) ⇒ 全局处理器按 @ApiResult 出 {code,message,data}。
}
