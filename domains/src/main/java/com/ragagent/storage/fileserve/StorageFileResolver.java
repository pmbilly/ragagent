package com.ragagent.storage.fileserve;

import com.ragagent.contract.storage.StorageEnvLookup;
import com.ragagent.contract.storage.fileserve.FileContentService;
import com.ragagent.contract.storage.fileserve.WritableFileContentService;
import com.ragagent.contract.tenant.StorageEngineConfig;
import com.ragagent.contract.tenant.TenantConfigLookup.TenantStorageView;
import com.ragagent.platform.security.CryptoService;
import com.ragagent.platform.storage.config.StorageRuntimeEnv;
import com.ragagent.platform.storage.fileserve.StoragePaths;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.storage.config.StorageProviderEnv;
import com.ragagent.storage.domain.StorageBackend;
import com.ragagent.storage.provider.FileService;
import com.ragagent.storage.provider.FileServiceFactory;
import com.ragagent.storage.repository.StorageBackendRepository;
import com.ragagent.storage.service.ResourceCatalogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * 运行时存储解析器，三个职责：
 *
 * <ul>
 *   <li>{@code resolveBackend / resolveFileService / hydrateTenantStorage}
 *       （backendID 优先、provider 是 legacy 回落）；</li>
 *   <li>{@code newFileServiceFromStorageConfig}（provider 完备性检查——错误文案会
 *       出现在 presigned-preview 的 400 body 里，是固定线格式）；</li>
 *   <li>{@code resolveTenantFileServiceWithFallback}（租户解析失败且 provider 等于
 *       全局 STORAGE_TYPE 时回落进程级服务）。</li>
 * </ul>
 *
 * <h2>云 provider 的 SDK 客户端</h2>
 * <p>配置完备的 minio/cos/tos/s3/oss/obs/ks3 经 {@code FileServiceFactory}
 * （八个 provider 实现：local + S3 协议族 + 三家厂商原生 SDK）造真实客户端，
 * 再由 {@link ProviderFileContentService} 适配回本包的 {@link FileContentService}。
 * 配置<b>不完备</b>时的错误文案由工厂产出
 * （"missing minio config" / "incomplete cos config" / {@code unsupported provider "%s"}）。
 * local 一支走 {@link LocalFileContentService}（与 provider 工厂的 local 语义一致；
 * 收敛为单一实现属清理项）。</p>
 *
 * <p>同时承载 resource catalog 装饰器的可见行为：
 * 打开 {@code resource://} 手柄先换物理路径；{@code GetFileURL} 对手柄在
 * APP_EXTERNAL_URL 在位时派生 /r/ 能力令牌。</p>
 */
@Service
public class StorageFileResolver {

    private static final Logger log = LoggerFactory.getLogger(StorageFileResolver.class);

    /** 进程级默认解密器（与 Spring 的 CryptoService 同语义：无状态，getAESKey() 读 env）。 */
    private static final CryptoService CRYPTO = new CryptoService();

    private final StorageBackendRepository backendRepo;
    private final ResourceCatalogService catalog;
    private final Map<String, StorageProviderEnv.ProviderEnvFamily> providerEnvs;

    @Autowired
    public StorageFileResolver(StorageBackendRepository backendRepo, ResourceCatalogService catalog,
            List<StorageProviderEnv.ProviderEnvFamily> providerEnvs) {
        this.backendRepo = backendRepo;
        this.catalog = catalog;
        this.providerEnvs = new LinkedHashMap<>();
        if (providerEnvs != null) {
            for (StorageProviderEnv.ProviderEnvFamily env : providerEnvs) {
                this.providerEnvs.put(env.provider(), env);
            }
        }
    }

    /**
     * 纯单元测试/无装配场景的便捷构造：只带 {@code local} 族——与「未设置 {@code STORAGE_TYPE}」
     * 的 env 缺省（provider=local、无 pathPrefix）同形，故既有测试的解析结果不变。
     */
    public StorageFileResolver(StorageBackendRepository backendRepo, ResourceCatalogService catalog) {
        this(backendRepo, catalog, List.of(new StorageProviderEnv.Local(null)));
    }

    // ── 解析结果 ────────────────────────────────────────────────────────────

    /** (fileSvc, resolvedProvider, ok)——错误经 {@code error} 通道。 */
    public record Resolution(FileContentService service, String resolvedProvider, String error) {

        boolean ok() {
            return error == null;
        }
    }

    // ── storagebackend.ResolveFileService / ResolveBackend ──────────────────

    /**
     * 返回的 Resolution.error 非 null 时 HTTP 面按各自的映射处理。
     */
    public Resolution resolveFileService(TenantStorageView tenant, String backendId, String provider,
            String localBaseDir) {
        if (tenant == null) {
            return new Resolution(null, "", "workspace context missing");
        }
        TenantStorageView hydrated = hydrateTenantStorage(tenant);
        Objects.requireNonNull(hydrated);

        BackendResolution backend = resolveBackend(hydrated, backendId, provider);
        if (backend.error() != null) {
            return new Resolution(null, "", backend.error());
        }
        if (backend.backend() != null) {
            StorageBackend b = backend.backend();
            FactoryResult inner = newFileServiceFromStorageConfig(b.getProvider(),
                    toStorageEngineConfig(b), localBaseDir);
            if (inner.error() != null) {
                return new Resolution(null, inner.provider(), inner.error());
            }
            // backend 在位 → 包 BackendScoped（GetFileURL 的
            // storage://<id>/ 包装与 GetFile 的 mismatch 守卫都来自它），
            // 再包 resource catalog 装饰器。
            return new Resolution(
                    decorate(new BackendScopedFileService(b.getId(), inner.service())),
                    inner.provider(), null);
        }
        JsonNode sec = JsonMappers.toJsonNode(hydrated.storageEngineConfig());
        String provider0 = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (provider0.isEmpty() && storageEngineDefaultProvider(sec).isEmpty()) {
            StorageBackend env = storageBackendFromEnvironment(hydrated.tenantId());
            if (env != null) {
                provider0 = env.getProvider();
                if (sec == null) {
                    sec = toStorageEngineConfig(env);
                }
            }
        }
        FactoryResult inner = newFileServiceFromStorageConfig(provider0, sec, localBaseDir);
        if (inner.error() != null) {
            return new Resolution(null, inner.provider(), inner.error());
        }
        return new Resolution(decorate(inner.service()), inner.provider(), null);
    }

    private record BackendResolution(StorageBackend backend, String error) {
    }

    /** backend 解析：legacy alias 与默认后端两级回落。 */
    private BackendResolution resolveBackend(TenantStorageView tenant, String backendId, String provider) {
        String id = backendId == null ? "" : backendId.trim();
        String p = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty() && !p.isEmpty()) {
            StorageBackend alias = backendRepo.findLegacyAlias(tenant.tenantId(), p);
            if (alias != null) {
                return new BackendResolution(alias, null);
            }
        }
        if (id.isEmpty()) {
            id = tenant.defaultStorageBackendId() == null ? "" : tenant.defaultStorageBackendId().trim();
        }
        if (!id.isEmpty()) {
            StorageBackend backend = backendRepo.getByID(tenant.tenantId(), id).orElse(null);
            if (backend == null) {
                return new BackendResolution(null, "storage backend not found");
            }
            if (!"active".equals(backend.getStatus())) {
                return new BackendResolution(null, "storage backend is not active");
            }
            return new BackendResolution(backend, null);
        }
        return new BackendResolution(null, null);
    }

    /** stub TenantStorageView 从库行补 DefaultStorageBackendId/配置。 */
    private TenantStorageView hydrateTenantStorage(TenantStorageView tenant) {
        if (tenant.tenantId() == 0 || backendRepo == null) {
            // 仓储缺位（纯单元测试构造）时跳过补全：本方法只是"从库行补 stub 字段"的优化，
            // 补不了就按调用方给的实体走。
            return tenant;
        }
        if (tenant.defaultStorageBackendId() != null && !tenant.defaultStorageBackendId().trim().isEmpty()) {
            return tenant;
        }
        String storedDefault = backendRepo.tenantDefaultBackendId(tenant.tenantId());
        if (storedDefault != null && !storedDefault.isEmpty()) {
            return new TenantStorageView(tenant.tenantId(), storedDefault, tenant.storageEngineConfig());
        }
        return tenant;
    }

    private static String storageEngineDefaultProvider(JsonNode sec) {
        if (sec == null) {
            return "";
        }
        JsonNode dp = sec.get("defaultProvider");
        if (dp == null || !dp.isString()) {
            return "";
        }
        return dp.asString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ── 配置 → FileService 工厂 ─────────────────────────────────────────────

    public record FactoryResult(FileContentService service, String provider, String error) {
    }

    /**
     * provider 可空 → 租户
     * defaultProvider；完备性检查的错误文案为固定线格式（presigned-preview 400 可见）。
     */
    public FactoryResult newFileServiceFromStorageConfig(String provider, JsonNode sec, String localBaseDir) {
        String p = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (p.isEmpty() && sec != null) {
            p = storageEngineDefaultProvider(sec);
        }
        if (p.isEmpty()) {
            return new FactoryResult(null, "", "empty provider");
        }
        String baseDir = localBaseDir == null || localBaseDir.isEmpty()
                ? StoragePaths.localStorageBaseDir() : localBaseDir;

        if ("local".equals(p)) {
            String dir = baseDir;
            JsonNode local = sec == null ? null : sec.get("local");
            if (local != null) {
                String pathPrefix = textOrNull(local.get("pathPrefix"));
                if (pathPrefix != null && !pathPrefix.trim().isEmpty()) {
                    String joined = LocalFileContentService.joinPath(dir, pathPrefix.trim());
                    try {
                        dir = LocalFileContentService.safePathUnderBase(dir, joined);
                    } catch (Exception ignored) {
                        // safePathUnderBase 失败 → 忽略，baseDir 原样
                    }
                }
            }
            String externalURL = env("APP_EXTERNAL_URL");
            return new FactoryResult(new LocalFileContentService(dir, externalURL), p, null);
        }
        // 云 provider 的完备性校验与文案由 FileServiceFactory 单点产出；
        // 这里不再逐 provider 重复一套 switch（两套字符串曾各自维护）。
        return providerBacked(p, sec, baseDir);
    }

    /**
     * 配置完备的云 provider → 经 {@code FileServiceFactory} 造真实 SDK 客户端。
     *
     * <p>完备性校验的文案由工厂产出（与上面各分支的字符串一致），因此
     * 这里不需要重复校验；{@code IllegalArgumentException} 就是"配置不完整"的通道。
     * 构造期的凭据/网络失败（真连桶）折成同一 error 通道——调用方按各自映射处理
     * （presigned-preview 折 400）。</p>
     */
    private static FactoryResult providerBacked(String p, JsonNode sec, String baseDir) {
        ProviderResolution pr = buildProviderRaw(p, sec, baseDir);
        if (!pr.ok()) {
            return new FactoryResult(null, pr.provider(), pr.error());
        }
        return new FactoryResult(new ProviderFileContentService(pr.service()), pr.provider(), null);
    }

    // ── 写面（A3-3 尾批）：知识上传/读取用的**原始** provider 服务 ──────────────

    /** provider 服务的原始解析结果（未经 resource catalog 装饰——写面用）。 */
    public record ProviderResolution(FileService service,
                                     String provider, String error) {
        public boolean ok() {
            return error == null && service != null;
        }
    }

    /**
     * 写面解析：按
     * backend 优先 → 环境回归 → 租户 defaultProvider 的顺序解析出**原始** provider
     * 服务（知识上传的 {@code SaveFile} / 读取的 {@code GetFile} 直连它，不经过
     * resource catalog 装饰——装饰层是给 HTTP 流式面用的）。
     *
     * <p>{@code local} 或 provider 为空时 {@code service == null} 且 {@code error == null}：
     * 调用方走 Java 既有的本地契约（{@code resource://…} + {@code LocalStorageService}），
     * 那是 golden 锁定的落盘形态，不能换成 provider 的 {@code local://…}。</p>
     */
    public ProviderResolution resolveProviderService(TenantStorageView tenant, String backendId, String provider,
            String localBaseDir) {
        if (tenant == null) {
            return new ProviderResolution(null, "", "workspace context missing");
        }
        TenantStorageView hydrated = hydrateTenantStorage(tenant);
        BackendResolution backend = resolveBackend(hydrated, backendId, provider);
        if (backend.error() != null) {
            return new ProviderResolution(null, "", backend.error());
        }
        if (backend.backend() != null) {
            return buildProviderRaw(backend.backend().getProvider(),
                    toStorageEngineConfig(backend.backend()), localBaseDir);
        }
        JsonNode sec = JsonMappers.toJsonNode(hydrated.storageEngineConfig());
        String p0 = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (p0.isEmpty() && storageEngineDefaultProvider(sec).isEmpty()) {
            StorageBackend env = storageBackendFromEnvironment(hydrated.tenantId());
            if (env != null) {
                p0 = env.getProvider();
                if (sec == null) {
                    sec = toStorageEngineConfig(env);
                }
            }
        }
        return buildProviderRaw(p0, sec, localBaseDir);
    }

    /** {@code local}/空 provider → (null, provider, null)；云 → 真客户端或错误文案。 */
    private static ProviderResolution buildProviderRaw(String provider, JsonNode sec, String baseDir) {
        String p = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (p.isEmpty() && sec != null) {
            p = storageEngineDefaultProvider(sec);
        }
        if (p.isEmpty()) {
            return new ProviderResolution(null, "", "empty provider");
        }
        if ("local".equals(p)) {
            return new ProviderResolution(null, p, null);
        }
        String dir = baseDir == null || baseDir.isEmpty()
                ? StoragePaths.localStorageBaseDir() : baseDir;
        try {
            StorageEngineConfig typed = sec == null
                    ? new StorageEngineConfig()
                    : CONFIG_MAPPER.convertValue(sec,
                            StorageEngineConfig.class);
            FileServiceFactory.Created created =
                    FileServiceFactory.fromStorageConfig(p, typed, dir);
            if (created == null || created.service() == null) {
                return new ProviderResolution(null, p, "unsupported provider \"" + p + "\"");
            }
            return new ProviderResolution(created.service(), p, null);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            log.warn("build provider file service failed: provider={} err={}", p, msg);
            return new ProviderResolution(null, p, msg);
        }
    }

    private static final tools.jackson.databind.ObjectMapper CONFIG_MAPPER =
            JsonMappers.lenient();
    private static String textOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asString();
    }

    private static String textOr(JsonNode n, String def) {
        return n == null || n.isNull() ? def : n.asString();
    }

    /**
     * provider 家族键读取（{@code MINIO_*} / {@code OBS_*} / {@code APP_EXTERNAL_URL} …）——
     * 走存储域统一查找面；未配置 → 空串。
     */
    static String env(String key) {
        String v = StorageEnvLookup.get(key);
        return v == null ? "" : v.trim();
    }

    // ── StorageBackend.ToStorageEngineConfig / FromEnvironment ─────────────

    /** 实例模型 → 单例配置投影。 */
    static JsonNode toStorageEngineConfig(StorageBackend b) {
        return toStorageEngineConfig(b, CRYPTO);
    }

    /**
     * 实例行 → provider 段（**凭据必须解密**）。
     *
     * <p>jsonb 里的私钥是**存储密文**（{@code enc:v1:…}，见
     * {@code StorageBackendService.serializeConfig} / {@code configOf}）。按存储层的
     * 同一语义（{@code decryptStoredSecret}：带前缀才解密、无前缀原样）就地解密
     * 两族命名，否则云读一律 403（S3: {@code The Access Key Id you provided does not
     * exist in our records}）。</p>
     *
     * <p>测试可注入 crypto：{@code getAESKey()} 读 32 字节 env，单测用子类固定密钥。</p>
     */
    static JsonNode toStorageEngineConfig(StorageBackend b, CryptoService crypto) {
        var cfg = new tools.jackson.databind.node.ObjectNode(
                JsonMappers.lenient().getNodeFactory());
        cfg.put("defaultProvider", b.getProvider());
        JsonNode c = b.getConfig();
        switch (b.getProvider() == null ? "" : b.getProvider()) {
            case "local" -> {
                var local = cfg.putObject("local");
                local.put("pathPrefix", textValue(c, "pathPrefix"));
            }
            default -> {
                // local 之外只需 defaultProvider
                // 与 provider 段（完备性检查读 sec.<provider>.*）。实例行配置是 camelCase
                //（StorageConfig 键名=字段名），这里改写成引擎面既定的 snake 键；凭据先解密。
                if (c != null) {
                    var copy = c.deepCopy();
                    renameConfigKeys(copy, b.getProvider());
                    decryptCredentials(copy, crypto);
                    cfg.set(b.getProvider(), copy);
                }
            }
        }
        return cfg;
    }

    /**
     * 行配置（camelCase）→ 各 provider 段字段名。自由键原样保留。
     *
     * <p><b>凭据两键按 provider 分族</b>：各段的字段名并不统一——
     * {@code MinioEngineConfig} 认 {@code accessKeyId}/{@code secretAccessKey}、
     * {@code CosEngineConfig} 认 {@code secretId}/{@code secretKey}、
     * 其余（s3/tos/oss/ks3/obs）认 {@code accessKey}/{@code secretKey}。</p>
     *
     * <p>此前这里统一改写成 minio 形态，于是<b>除 minio 外的行配置凭据被 Jackson 静默丢弃</b>
     * （转换器配了 {@code FAIL_ON_UNKNOWN_PROPERTIES=false}，未知键不报错）——云读退化为
     * 「无凭据」（403/匿名），且行校验恰好「两边都空 ⇒ 通过」不报错。护栏见
     * {@code ProviderWiringTest#backendRowCredentialsBindToProviderSection}。</p>
     */
    private static void renameConfigKeys(tools.jackson.databind.JsonNode providerConfig, String provider) {
        if (!(providerConfig instanceof tools.jackson.databind.node.ObjectNode obj)) {
            return;
        }
        String p = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String accessField = switch (p) {
            case "minio" -> "accessKeyId";
            case "cos" -> "secretId";
            default -> "accessKey";
        };
        String secretField = "minio".equals(p) ? "secretAccessKey" : "secretKey";
        moveKey(obj, "accessKeyId", accessField);
        moveKey(obj, "secretAccessKey", secretField);
    }

    /** 改键：目标键已存在时保留现状（行里显式给的引擎面键优先）。 */
    private static void moveKey(tools.jackson.databind.node.ObjectNode obj, String from, String to) {
        var v = obj.remove(from);
        if (v != null && !obj.has(to)) {
            obj.set(to, v);
        }
    }

    /** 解密两族凭据命名（只有带 {@code enc:v1:} 前缀的才是密文，其余原样——照存储层语义）。 */
    private static void decryptCredentials(tools.jackson.databind.JsonNode providerConfig,
            CryptoService crypto) {
        for (String field : new String[]{
                "accessKeyId", "secretAccessKey", "secretId", "secretKey", "accessKey"}) {
            tools.jackson.databind.JsonNode value = providerConfig.get(field);
            if (value != null && value.isString()) {
                ((tools.jackson.databind.node.ObjectNode) providerConfig)
                        .put(field, crypto.decryptStoredSecret(value.asString()));
            }
        }
    }

    private static String textValue(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asString();
    }

    /**
     * env 快照的 System 只读行。
     *
     * <p>配置不由本类手写 switch 拼装，而是复用**同一个投影**
     * （{@code StorageProviderEnv.*.writeConfig}，落库面 camel 词汇，与
     * {@code DefaultStorageBackendProvisioner} 同源）——本行随后照常经
     * {@code toStorageEngineConfig} 的改名器派生出引擎面。同一份环境变量<b>只有一条
     * 投影路径</b>，键名漂移不再可能。</p>
     */
    StorageBackend storageBackendFromEnvironment(long tenantId) {
        String provider = StorageRuntimeEnv.storageType()
                .trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.isEmpty()) {
            provider = "local";
        }
        StorageProviderEnv.ProviderEnvFamily family = providerEnvs.get(provider);
        if (family == null) {
            // 与改前同形：环境里没有这个 provider 的快照 / 未知 provider → null
            return null;
        }
        StorageBackend b = new StorageBackend();
        b.setTenantId(tenantId);
        b.setName("System " + provider.toUpperCase(java.util.Locale.ROOT));
        b.setProvider(provider);
        b.setSource("env");
        b.setStatus("active");
        b.setLegacyAlias(true);
        var cfg = JsonMappers.lenient().createObjectNode();
        family.writeConfig(cfg);
        b.setConfig(cfg);
        return b;
    }

    // ── 租户级解析失败回落 ──────────────────────────────────────────────────

    /**
     * 租户级解析失败且
     * provider == 全局 STORAGE_TYPE 时回落进程级服务；否则 ok=false（调用方 400）。
     */
    public Resolution resolveTenantFileServiceWithFallback(String logTag, TenantStorageView tenant,
            String backendId, String provider, String absDir, FileContentService globalFileService) {
        Resolution resolution = resolveFileService(tenant, backendId, provider, absDir);
        if (resolution.ok()) {
            return resolution;
        }
        String globalStorageType = StoragePaths.globalStorageType();
        if (provider != null && provider.equals(globalStorageType) && globalFileService != null) {
            log.info("[Router] {} tenant storage config missing or invalid, fallback to global file "
                    + "service: tenant_id={} provider={} err={}", logTag, tenant.tenantId(), provider,
                    resolution.error());
            return new Resolution(new ResourceCatalogFileService(globalFileService, catalog), globalStorageType, null);
        }
        log.warn("[Router] {} resolve file service failed without fallback: tenant_id={} provider={} "
                + "global_storage_type={} err={}", logTag, tenant.tenantId(), provider, globalStorageType,
                resolution.error());
        return new Resolution(null, "", resolution.error());
    }

    // ── resourceCatalogFileService 装饰器 ───────────────────────────────────

    /**
     * 生产装配的进程级默认服务（恒 local 基座）。
     * baseDir 经 Spring 属性注入（env 缺省，测试期可注入——见 FileProxyService）。
     */
    public WritableFileContentService globalFileService(String localBaseDir) {
        return decorate(new LocalFileContentService(localBaseDir, env("APP_EXTERNAL_URL")));
    }

    /** 测试/装饰辅助：给定 inner 的 resource:// 装饰视图（读+写）。 */
    public WritableFileContentService decorate(FileContentService inner) {
        return new ResourceCatalogFileService(inner, catalog);
    }
}
