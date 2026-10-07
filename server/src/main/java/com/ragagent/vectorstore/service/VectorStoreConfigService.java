package com.ragagent.vectorstore.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;
import com.ragagent.common.security.SsrfGuard;
import com.ragagent.vectorstore.domain.ConnectionConfig;
import com.ragagent.vectorstore.domain.EnvVectorStores;
import com.ragagent.vectorstore.domain.IndexConfig;
import com.ragagent.vectorstore.domain.VectorStore;
import com.ragagent.vectorstore.domain.VectorStoreEngines;
import com.ragagent.vectorstore.mapper.VectorStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ragagent.common.retrieval.RetrievalDriverProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 向量库配置服务（CRUD + 连通性探测 + 健康检查）。命名带 Config 以区别
 * retrieval.engine.VectorStoreService（检索引擎的 embeddings 索引写面）。
 *
 * <p>校验顺序固定：Validate → validateConnectionConfig → validateConnectionAddrSSRF →
 * ValidateIndexConfig → OpenSearch HNSW → DB 去重 → env 去重 → TestConnection（失败=
 * "connection test failed: ..."）→ 落库 → 注册（进程内注册表当前 no-op）。</p>
 *
 * <p><b>TestConnection 的引擎覆盖（已知差异，记入报告）</b>：elasticsearch 走
 * 裸 HTTP GET（含 basic auth、不跟随重定向、版本解析）；milvus 为
 * TCP 拨号（version 恒 ""）；postgres 用 JDBC 探测；weaviate/opensearch 走 HTTP 探测；
 * qdrant/tencent/doris 以 TCP/JDBC 探测替代——
 * 连接被拒的**错误文案逐字一致**，成功路径的 version 探测弱化为 ""。</p>
 */
@Service
public class VectorStoreConfigService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfigService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_SHARDS = 64;
    private static final int MAX_REPLICAS = 10;
    private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,127}$");

    private final VectorStoreRepository repo;
    private final SsrfGuard ssrfGuard;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    /** 构造期派生一次的 env stores */
    private final EnvVectorStores.EnvLookup envLookup;

    /** RETRIEVE_DRIVER（属性绑定，不读裸 env）。 */
    private final RetrievalDriverProperties driverProperties;

    private final List<VectorStore> envStores;

    public VectorStoreConfigService(VectorStoreRepository repo, SsrfGuard ssrfGuard,
            JdbcTemplate jdbc, TransactionTemplate tx,
            RetrievalDriverProperties driverProperties, EnvVectorStores.EnvLookup envLookup) {
        this.repo = repo;
        this.ssrfGuard = ssrfGuard;
        this.jdbc = jdbc;
        this.tx = tx;
        this.envLookup = envLookup;
        this.driverProperties = driverProperties;
        this.envStores = EnvVectorStores.build(driverProperties.driver(), envLookup);
    }

    public List<VectorStore> envStores() {
        return envStores;
    }

    /**
     * 按 id 取进程级（{@code __env_*}）向量库；不存在返回 null。
     *
     * <p>env 族查找面由 {@link EnvVectorStores.EnvLookup} bean 提供（调用方
     * 不再自传 {@code System::getenv}）。</p>
     */
    public VectorStore findEnvStore(String id) {
        return EnvVectorStores.find(driverProperties.driver(), envLookup, id);
    }

    public VectorStore getByID(long tenantId, String id) {
        return repo.getByID(tenantId, id);
    }

    public List<VectorStore> list(long tenantId) {
        return repo.list(tenantId);
    }

    /** 在副本上写 connection_config.version */
    public void saveDetectedVersion(VectorStore store) {
        repo.updateConnectionConfig(store);
    }

    /** 创建向量库（步骤注释为固定序）。 */
    public void create(VectorStore store) {
        // 1. 基础校验
        store.validate();
        // 2. 引擎专属连接必填
        validateConnectionConfig(store.getEngineType(), store.getConnectionConfig());
        // 2.1 SSRF（先于一切网络 I/O）
        validateConnectionAddrSSRF(store.getEngineType(), store.getConnectionConfig());
        // 2.5 IndexConfig 边界/名字
        validateIndexConfig(store.getIndexConfig());
        // 2.6 OpenSearch HNSW（仅 create）
        if ("opensearch".equals(store.getEngineType())) {
            validateOpenSearchIndexConfig(store.getIndexConfig());
        }
        // 3. DB 去重（应用层比较——JSONB 抽取语法因库而异）
        String endpoint = store.getConnectionConfig().getEndpoint();
        String indexName = store.getIndexConfig().getIndexNameOrDefault(store.getEngineType());
        for (VectorStore s : repo.list(store.getTenantId())) {
            if (s.getEngineType().equals(store.getEngineType())
                    && s.getConnectionConfig().getEndpoint().equals(endpoint)
                    && s.getIndexConfig().getIndexNameOrDefault(store.getEngineType()).equals(indexName)) {
                throw BizException.conflict("a vector store with the same endpoint and index already exists");
            }
        }
        // 4. env 去重
        for (VectorStore envStore : envStores) {
            if (envStore.getEngineType().equals(store.getEngineType())
                    && envStore.getConnectionConfig().getEndpoint().equals(endpoint)
                    && envStore.getIndexConfig().getIndexNameOrDefault(store.getEngineType()).equals(indexName)) {
                throw BizException.conflict(
                        "a vector store with the same endpoint and index is already configured via environment variables");
            }
        }
        // 5. 连接测试（版本探测）：err → NewBadRequestError("connection test failed: %s. Ensure ...")
        String version;
        try {
            version = testConnection(store.getEngineType(), store.getConnectionConfig());
        } catch (BizException e) {
            throw BizException.badRequest("connection test failed: " + e.getMessage()
                    + ". Ensure the server is reachable before saving.");
        }
        if (!version.isEmpty()) {
            store.getConnectionConfig().version = version;
        }
        // 6. 落库
        repo.create(store, java.time.OffsetDateTime.now());
        // 7. 进程内注册表（引擎工厂未实现，当前 no-op）
    }

    public void updateName(VectorStore store) {
        if (store.getTenantId() == null || store.getTenantId() == 0) {
            throw validation("tenant_id is required");
        }
        if (store.getName() == null || store.getName().isEmpty()) {
            throw validation("name is required");
        }
        repo.updateName(store, java.time.OffsetDateTime.now());
    }

    /** 事务内行锁（PG FOR UPDATE，H2 无锁提示）+ 绑定计数 → 软删 */
    public void delete(long tenantId, String id) {
        Integer deleted = tx.execute(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vector_stores WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                    Integer.class, id, tenantId);
            if (count == null || count == 0) {
                throw BizException.notFound("vector store not found");
            }
            Integer bound = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_bases WHERE tenant_id = ? AND vector_store_id = ? "
                            + "AND deleted_at IS NULL",
                    Integer.class, tenantId, id);
            if (bound != null && bound > 0) {
                throw BizException.badRequest("vector store still has " + bound
                        + " knowledge base(s) bound to it; unbind or delete them before removing the store");
            }
            jdbc.update("UPDATE vector_stores SET deleted_at = NOW() "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", id, tenantId);
            return 1;
        });
        if (deleted == null || deleted == 0) {
            throw BizException.notFound("vector store not found");
        }
        // 进程内注册表未实现，当前 no-op
    }

    /** 白名单 → 必填 → SSRF → TestConnection。失败抛 AppError */
    public String testRawConnection(String engineType, ConnectionConfig config) {
        if (!VectorStoreEngines.isValidEngineType(engineType)) {
            throw validation("connection test is not supported for engine type: " + engineType);
        }
        validateConnectionConfig(engineType, config);
        validateConnectionAddrSSRF(engineType, config);
        return testConnection(engineType, config);
    }

    /**
     * 连通性测试：成功返回探测版本（可空），
     * 失败抛 AppError（各分支均为 code 1000，
     * handler 以**双前缀**形态输出——golden vs-test-byid-connrefused 钉住）。
     */
    public String testConnection(String engineType, ConnectionConfig config) {
        try {
            return switch (engineType == null ? "" : engineType) {
                case "elasticsearch" -> testElasticsearch(config);
                case "postgres" -> testPostgres(config);
                case "qdrant" -> testQdrant(config);
                case "milvus" -> testMilvus(config);
                case "tencent_vectordb" -> testTencentVectorDB(config);
                case "weaviate" -> testWeaviate(config);
                case "doris" -> testDoris(config);
                case "opensearch" -> testOpenSearch(config);
                case "sqlite" -> "";
                default -> throw BizException.badRequest(
                        "connection test not supported for engine type: " + engineType);
            };
        } catch (ConnectorFailure e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /** 各探测器的失败载体（统一落 code 1000 AppError） */
    private static class ConnectorFailure extends RuntimeException {
        ConnectorFailure(String message) {
            super(message);
        }
    }

    // ── 各引擎探测（错误文案逐字固定，是契约） ──────────────────────────

    private String testElasticsearch(ConnectionConfig config) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TEST_TIMEOUT).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(TEST_TIMEOUT).GET();
        try {
            builder.uri(URI.create(config.addr));
        } catch (RuntimeException e) {
            throw new ConnectorFailure("failed to create elasticsearch request: invalid address");
        }
        if (config.username != null && !config.username.isEmpty()) {
            String token = Base64.getEncoder().encodeToString(
                    (config.username + ":" + (config.password == null ? "" : config.password))
                            .getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        HttpResponse<String> resp;
        try {
            resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConnectorFailure("failed to connect to elasticsearch: connection refused or authentication failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorFailure("failed to connect to elasticsearch: connection refused or authentication failed");
        }
        if (resp.statusCode() != 200) {
            throw new ConnectorFailure("failed to connect to elasticsearch: authentication failed or server error");
        }
        try {
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode number = root.path("version").path("number");
            return number.isMissingNode() ? "" : number.asText("");
        } catch (Exception e) {
            return ""; // connected but version unparseable
        }
    }

    private String testPostgres(ConnectionConfig config) {
        if (config.useDefaultConnection) {
            return ""; // 默认连接恒可达；无 handle 查版本
        }
        String addr = config.addr == null ? "" : config.addr;
        String jdbcUrl = addr.startsWith("jdbc:")
                ? addr
                : "jdbc:postgresql://" + addr.replaceFirst("^postgres(ql)?://", "");
        try (var conn = java.sql.DriverManager.getConnection(jdbcUrl,
                config.username == null ? "" : config.username,
                config.password == null ? "" : config.password)) {
            var rs = conn.createStatement().executeQuery("SHOW server_version");
            return rs.next() ? rs.getString(1) : "";
        } catch (Exception e) {
            throw new ConnectorFailure("failed to connect to postgres: connection refused or authentication failed");
        }
    }

    /**
     * qdrant 探测：走驱动的健康探针
     * （gRPC HealthCheck → REST {@code GET /}），返回 {@code version}；失败折叠成通用文案。
     */
    private String testQdrant(ConnectionConfig config) {
        int port = config.port == 0 ? 6334 : config.port;
        try {
            return com.ragagent.retrieval.engine.qdrant.QdrantRetrieveRepository.testConnection(
                    config.host, port, config.apiKey, config.useTls, ssrfGuard);
        } catch (RuntimeException e) {
            log.warn("Qdrant connection test failed: {}", e.getMessage());
            throw new ConnectorFailure(
                    "failed to connect to qdrant: connection refused or authentication failed");
        }
    }

    /**
     * milvus 探测：<b>TCP 拨号</b>语义（不做 SDK 级验证），因此<b>版本恒空</b>。
     * 本仓已有 REST v2 客户端 → 升级为 {@code collections/list} 探针
     * （连通性 + 认证都验到，比 TCP 拨号更强），仍返回 ""（Milvus 无版本端点）。
     */
    private String testMilvus(ConnectionConfig config) {
        try {
            return com.ragagent.retrieval.engine.milvus.MilvusRetrieveRepository.testConnection(
                    config.addr, config.username, config.password, config.database, ssrfGuard);
        } catch (RuntimeException e) {
            log.warn("Milvus connection test failed: {}", e.getMessage());
            throw new ConnectorFailure(
                    "failed to connect to milvus: connection refused or server unreachable");
        }
    }

    /**
     * tencent_vectordb 探测：{@code ListDatabase}
     * 探针——客户端构造失败（地址/用户名/密钥不合法）→ "connection refused or authentication
     * failed"；调用失败 → "authentication failed or server error"。版本恒 ""。
     */
    private String testTencentVectorDB(ConnectionConfig config) {
        try {
            return com.ragagent.retrieval.engine.tencentvectordb.TencentVectorDbRetrieveRepository
                    .testConnection(config.addr, config.username, config.apiKey, ssrfGuard);
        } catch (com.ragagent.retrieval.engine.tencentvectordb.TencentVectorDbRestClient
                .TencentVectorDbApiException e) {
            log.warn("Tencent VectorDB list database failed: {}", e.getMessage());
            throw new ConnectorFailure(
                    "failed to connect to tencent vectordb: authentication failed or server error");
        } catch (RuntimeException e) {
            log.warn("Tencent VectorDB connection test failed: {}", e.getMessage());
            throw new ConnectorFailure(
                    "failed to connect to tencent vectordb: connection refused or authentication failed");
        }
    }

    private String testWeaviate(ConnectionConfig config) {
        String host = empty(config.host) ? "weaviate:8080" : config.host;
        String scheme = empty(config.scheme) ? "http" : config.scheme;
        HttpClient client = HttpClient.newBuilder().connectTimeout(TEST_TIMEOUT).build();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(scheme + "://" + host + "/v1/.well-known/ready"))
                    .timeout(TEST_TIMEOUT).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new ConnectorFailure("failed to connect to weaviate: server not ready or authentication failed");
            }
        } catch (IOException e) {
            throw new ConnectorFailure("failed to connect to weaviate: server not ready or authentication failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorFailure("failed to connect to weaviate: server not ready or authentication failed");
        }
        try {
            HttpRequest meta = HttpRequest.newBuilder(URI.create(scheme + "://" + host + "/v1/meta"))
                    .timeout(TEST_TIMEOUT).GET().build();
            HttpResponse<String> resp = client.send(meta, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode version = MAPPER.readTree(resp.body()).path("version");
                return version.isMissingNode() ? "" : version.asText("");
            }
        } catch (Exception e) {
            return ""; // connected but version unknown
        }
        return "";
    }

    /**
     * doris 探测：走 MySQL 协议驱动
     * 的完整探针——连接（Ping 语义）+ {@code SELECT @@version}（失败只 WARN，返回空版本）；
     * 版本串剥 {@code "Doris-"} 前缀（{@code "5.7.99 Doris-4.1.0"} → {@code "4.1.0"}）。
     * database 不强制：缺省用 information_schema。
     */
    private String testDoris(ConnectionConfig config) {
        if (empty(config.addr)) {
            throw new ConnectorFailure("failed to create doris connection: addr is required");
        }
        try {
            return com.ragagent.retrieval.engine.doris.DorisRetrieveRepository.testConnection(
                    config.addr, config.database, config.username, config.password);
        } catch (java.sql.SQLException e) {
            log.warn("Doris connection test failed: {}", e.getMessage());
            throw new ConnectorFailure(
                    "failed to connect to doris: connection refused or authentication failed");
        }
    }

    /**
     * opensearch 探测：走驱动的
     * 连通性探针（版本 + 每节点 k-NN 插件，TestConnection→构造期探针复用）；失败折叠
     * 成通用文案（不向 API 调用方暴露集群内部细节）。版本在探针内解析但不在此暴露
     * （lazy index 首用再校验）→ 恒 ""。
     */
    private String testOpenSearch(ConnectionConfig config) {
        String generic = "failed to connect to opensearch: check address, credentials, version (>= 2.4), "
                + "and that the k-NN plugin is installed";
        if (empty(config.addr)) {
            throw new ConnectorFailure("failed to create opensearch connection: addr is required");
        }
        try {
            com.ragagent.retrieval.engine.opensearch.OpenSearchRetrieveRepository.testConnection(
                    config.addr, config.username, config.password, config.insecureSkipVerify,
                    ssrfGuard);
        } catch (RuntimeException e) {
            log.warn("OpenSearch connection test failed: {}", e.getMessage());
            throw new ConnectorFailure(generic);
        }
        return "";
    }

    // ── 校验（文案逐字对照） ────────────────────────────────────────────

    /** 连接配置必填校验 */
    public void validateConnectionConfig(String engineType, ConnectionConfig config) {
        ConnectionConfig c = config == null ? new ConnectionConfig() : config;
        switch (engineType == null ? "" : engineType) {
            case "elasticsearch" -> require(c.addr, "addr is required for elasticsearch");
            case "postgres" -> {
                if (!c.useDefaultConnection && empty(c.addr)) {
                    throw validation("addr or use_default_connection is required for postgres");
                }
            }
            case "qdrant" -> require(c.host, "host is required for qdrant");
            case "milvus" -> require(c.addr, "addr is required for milvus");
            case "tencent_vectordb" -> {
                require(c.addr, "addr is required for tencent_vectordb");
                require(c.username, "username is required for tencent_vectordb");
                require(c.apiKey, "api_key is required for tencent_vectordb");
            }
            case "weaviate" -> require(c.host, "host is required for weaviate");
            case "doris" -> {
                require(c.addr, "addr is required for doris (FE MySQL host:port)");
                require(c.database, "database is required for doris");
            }
            case "opensearch" -> require(c.addr, "addr is required for opensearch");
            case "sqlite" -> {
                // 文件型引擎，无连接配置
            }
            default -> {
                // 未知引擎在此直接通过（引擎白名单在 validate() 已拦）
            }
        }
    }

    /** 逐地址字段的 SSRF 校验（白名单优先），未知引擎 fail-closed */
    public void validateConnectionAddrSSRF(String engineType, ConnectionConfig config) {
        ConnectionConfig c = config == null ? new ConnectionConfig() : config;
        switch (engineType == null ? "" : engineType) {
            case "elasticsearch", "opensearch", "milvus", "tencent_vectordb", "doris" -> checkAddr(c.addr);
            case "qdrant" -> {
                String addr = c.host;
                if (!empty(addr) && c.port != 0) {
                    addr = addr + ":" + c.port;
                }
                checkAddr(addr);
            }
            case "weaviate" -> {
                checkAddr(c.host);
                checkAddr(c.grpcAddress);
            }
            case "sqlite" -> {
                // 无远端地址
            }
            default -> throw validation("SSRF validation is not configured for engine type: " + engineType);
        }
    }

    private void checkAddr(String addr) {
        if (empty(addr)) {
            return;
        }
        try {
            ssrfGuard.validateURLForSSRF(addr);
        } catch (SsrfGuard.SsrfException e) {
            throw validation(ssrfGuard.formatSSRFError("vector store address", addr, e));
        }
    }

    /** 索引配置校验 */
    public void validateIndexConfig(IndexConfig ic) {
        IndexConfig c = ic == null ? new IndexConfig() : ic;
        if (notEmpty(c.indexName) && !INDEX_NAME_PATTERN.matcher(c.indexName).matches()) {
            throw validation("index_name must start with a letter and contain only alphanumeric, "
                    + "underscore, or hyphen characters (max 128)");
        }
        if (notEmpty(c.collectionPrefix) && !INDEX_NAME_PATTERN.matcher(c.collectionPrefix).matches()) {
            throw validation("collection_prefix must start with a letter and contain only alphanumeric, "
                    + "underscore, or hyphen characters (max 128)");
        }
        if (notEmpty(c.collectionName) && !INDEX_NAME_PATTERN.matcher(c.collectionName).matches()) {
            throw validation("collection_name must start with a letter and contain only alphanumeric, "
                    + "underscore, or hyphen characters (max 128)");
        }
        checkRange("number_of_shards", c.numberOfShards, MAX_SHARDS);
        checkRange("number_of_replicas", c.numberOfReplicas, MAX_REPLICAS);
        checkRange("shard_number", c.shardNumber, MAX_SHARDS);
        checkRange("replication_factor", c.replicationFactor, MAX_REPLICAS);
        checkRange("shards_num", c.shardsNum, MAX_SHARDS);
        checkRange("replica_number", c.replicaNumber, MAX_REPLICAS);
        checkRange("desired_shard_count", c.desiredShardCount, MAX_SHARDS);
        checkRange("buckets_num", c.bucketsNum, MAX_SHARDS);
        checkRange("replication_num", c.replicationNum, MAX_REPLICAS);
    }

    private static void checkRange(String name, int value, int max) {
        if (value < 0 || value > max) {
            throw validation(name + " must be between 0 and " + max);
        }
    }

    /** OpenSearch 索引配置校验（HNSW 边界） */
    public void validateOpenSearchIndexConfig(IndexConfig ic) {
        IndexConfig c = ic == null ? new IndexConfig() : ic;
        if (c.hnswM != 0 && (c.hnswM < 2 || c.hnswM > 100)) {
            throw validation("hnsw_m must be between 2 and 100");
        }
        if (c.hnswEfConstruction != 0 && (c.hnswEfConstruction < 2 || c.hnswEfConstruction > 4096)) {
            throw validation("hnsw_ef_construction must be between 2 and 4096");
        }
        if (c.hnswEfSearch != 0 && (c.hnswEfSearch < 1 || c.hnswEfSearch > 10000)) {
            throw validation("hnsw_ef_search must be between 1 and 10000");
        }
        if (notEmpty(c.knnEngine) && !c.knnEngine.equals("lucene") && !c.knnEngine.equals("faiss")) {
            // 文案含真实引号（错误契约的一部分）
            throw validation("knn_engine must be \"lucene\" or \"faiss\"");
        }
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    private static void require(String value, String message) {
        if (empty(value)) {
            throw validation(message);
        }
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static BizException validation(String message) {
        return new BizException(AppError.validation(message));
    }
}
