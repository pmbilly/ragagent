package com.ragagent.retrieval.engine;


import com.ragagent.contract.vectorstore.ConnectionConfig;
import com.ragagent.contract.vectorstore.EnvStoreIds;
import com.ragagent.contract.vectorstore.IndexConfig;
import com.ragagent.contract.vectorstore.VectorStoreView;
import com.ragagent.platform.security.SsrfGuard;
import com.ragagent.retrieval.engine.doris.DorisRetrieveRepository;
import com.ragagent.retrieval.engine.elasticsearch.ElasticsearchV7RetrieveRepository;
import com.ragagent.retrieval.engine.elasticsearch.ElasticsearchV8RetrieveRepository;
import com.ragagent.retrieval.engine.milvus.MilvusRetrieveRepository;
import com.ragagent.retrieval.engine.opensearch.OpenSearchRetrieveRepository;
import com.ragagent.retrieval.engine.qdrant.QdrantRetrieveRepository;
import com.ragagent.retrieval.engine.sqlite.SqliteRetrieveRepository;
import com.ragagent.retrieval.engine.tencentvectordb.TencentVectorDbRetrieveRepository;
import com.ragagent.retrieval.engine.weaviate.WeaviateRetrieveRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 检索引擎工厂——从 {@link com.ragagent.contract.vectorstore.VectorStoreView} 配置按引擎类型构建检索引擎服务，
 * 并承担运行时地址的 SSRF 校验。
 *
 * <p><b>构造与地址策略在同一张注册表登记</b>（{@link EngineSpec}）：新增引擎时两者不可能分开漏改。
 * 地址字段为空且未声明免检的注册项会在类初始化时直接失败（fail-closed）。</p>
 *
 * <h2>各引擎落地形态</h2>
 * <ul>
 *   <li>**elasticsearch**：按 {@code connection_config.version} 前缀选 v7/v8（空 → v8），
 *       建 driver → 包成 {@link KeywordsVectorHybridRetrieveEngineService}</li>
 *   <li>**postgres**：不经本工厂——读/写由既有 JDBC 件承担（{@code PgVectorRetrieveRepository} /
 *       {@code VectorStoreService}），走到这里即抛"明确指引"式异常；
 *       **sqlite**：真落地，介质为独立 SQLite 文件（{@code SQLITE_PATH}，见驱动类注释）</li>
 *   <li>**opensearch**：k-NN 驱动 +
 *       audit sink 注入；探针在构造期显形</li>
 *   <li>**doris**：MySQL 协议主链路 + Stream Load
 *       HTTP；addr 必填、database 必填、http_port 缺省 8030</li>
 *   <li>**qdrant**：REST 自持（HTTP/JSON：host/port（缺省 6334）/api_key/
 *       use_tls）</li>
 *   <li>**weaviate**：REST 自持（GraphQL 检索/
 *       列举与批量删除本就是 REST；批量创建走客户端自身的 REST 回落路径；host 缺省
 *       {@code weaviate:8080}、scheme 缺省 http、api_key 直取）</li>
 *   <li>**milvus**：REST v2 自持（{@code /v2/vectordb/…}
 *       零新依赖：建集合含 BM25 函数/稀疏列/indexParams、upsert/query/search/delete 均已对真服务端
 *       实测；addr 缺省 {@code localhost:19530}）</li>
 *   <li>**tencent_vectordb**：HTTP API 自持
 *       （{@code Authorization: Bearer account=…&api_key=…}，同一服务端的 HTTP 面；
 *       BM25 稀疏向量客户端编码）</li>
 * </ul>
 *
 * <h2>地址策略（逐引擎）</h2>
 * <ul>
 *   <li>postgres/sqlite 免检（声明 {@code addressExempt}）；elasticsearch/opensearch/milvus/tencent/doris
 *       检 {@code addr}；qdrant 检 {@code host:port}（host 空或 port 0 时只检 host，去方括号）；
 *       weaviate 检 {@code host} + {@code grpc_address} 两处；未登记的类型报
 *       {@code vector store engine "<t>" has no SSRF address policy}；空地址放行；
 *       非空地址过 SSRF 校验，失败文案 {@code <label> failed SSRF validation: <err>}</li>
 *   <li>索引配置取值：shards 缺省 <b>0</b>、replicas 缺省 <b>-1</b>——
 *       0/-1 视为"未设置"，由各驱动按内部缺省落地</li>
 * </ul>
 */
public final class EngineFactory {

    private EngineFactory() {
    }

    /** 建不起来时的失败（调用方可据文案分类）。 */
    public static class EngineNotSupportedException extends RuntimeException {
        public EngineNotSupportedException(String message) {
            super(message);
        }
    }

    /** 构造一条引擎服务。 */
    @FunctionalInterface
    private interface EngineBuilder {
        KeywordsVectorHybridRetrieveEngineService build(VectorStoreView store, SsrfGuard guard,
                                                        OpenSearchRetrieveRepository.AuditSink auditSink);
    }

    /** 地址策略的一条：{@code label} 逐字进错误文案；取值返回空串＝未配置（放行）。 */
    private record AddressField(String label, Function<ConnectionConfig, String> value) {
    }

    /**
     * 引擎注册项。地址策略必须显式二选一——声明字段，或 {@code addressExempt}
     * （无远端地址 / 不走本工厂）。空字段 + 非免检＝登记错误，类初始化即失败。
     */
    private record EngineSpec(EngineBuilder builder, List<AddressField> addressFields,
                              boolean addressExempt) {

        EngineSpec {
            if (addressFields.isEmpty() && !addressExempt) {
                throw new IllegalArgumentException(
                        "engine spec needs address fields or addressExempt");
            }
        }
    }

    private static final Map<String, EngineSpec> SPECS = Map.ofEntries(
            Map.entry(EngineTypes.ENGINE_ELASTICSEARCH,
                    new EngineSpec(EngineFactory::elasticsearch,
                            List.of(addrField()), false)),
            Map.entry(EngineTypes.ENGINE_POSTGRES,
                    new EngineSpec(EngineFactory::postgresUnsupported, List.of(), true)),
            Map.entry(EngineTypes.ENGINE_SQLITE,
                    new EngineSpec(EngineFactory::sqlite, List.of(), true)),
            Map.entry(EngineTypes.ENGINE_OPENSEARCH,
                    new EngineSpec(EngineFactory::opensearch,
                            List.of(addrField()), false)),
            Map.entry(EngineTypes.ENGINE_DORIS,
                    new EngineSpec(EngineFactory::doris, List.of(addrField()), false)),
            Map.entry(EngineTypes.ENGINE_QDRANT,
                    new EngineSpec(EngineFactory::qdrant,
                            List.of(new AddressField("qdrant address", EngineFactory::qdrantEndpoint)),
                            false)),
            Map.entry(EngineTypes.ENGINE_WEAVIATE,
                    new EngineSpec(EngineFactory::weaviate,
                            List.of(new AddressField("weaviate HTTP address", cc -> cc.host),
                                    new AddressField("weaviate gRPC address", cc -> cc.grpcAddress)),
                            false)),
            Map.entry(EngineTypes.ENGINE_MILVUS,
                    new EngineSpec(EngineFactory::milvus, List.of(addrField()), false)),
            Map.entry(EngineTypes.ENGINE_TENCENT_VECTORDB,
                    new EngineSpec(EngineFactory::tencentVectorDb,
                            List.of(addrField()), false)));

    /**
     * 先做地址策略校验，再按引擎类型建服务。
     * {@code guard} 为空 = 测试口（跳过地址校验）。
     */
    public static KeywordsVectorHybridRetrieveEngineService createFromStore(VectorStoreView store,
                                                                            SsrfGuard guard) {
        return createFromStore(store, guard, null);
    }

    /**
     * 带 audit sink 的重载——opensearch 注入
     * {@code WithAuditSink}（索引创建/重索引事件）；其它引擎忽略。
     * {@code sink} 为 null = no-op（测试口）。
     */
    public static KeywordsVectorHybridRetrieveEngineService createFromStore(VectorStoreView store,
                                                                            SsrfGuard guard,
                                                                            OpenSearchRetrieveRepository.AuditSink auditSink) {
        EngineSpec spec = specFor(store.getEngineType());
        validate(spec, connectionConfig(store), guard);
        return spec.builder().build(store, guard, auditSink);
    }

    /** 逐引擎的地址策略 + SSRF 校验。 */
    static void validateRuntimeVectorStoreAddresses(VectorStoreView store, SsrfGuard guard) {
        validate(specFor(store.getEngineType()), connectionConfig(store), guard);
    }

    private static EngineSpec specFor(String engineType) {
        String type = engineType == null ? "" : engineType;
        EngineSpec spec = SPECS.get(type);
        if (spec == null) {
            throw new EngineNotSupportedException("vector store engine \"" + type
                    + "\" has no SSRF address policy");
        }
        return spec;
    }

    private static void validate(EngineSpec spec, ConnectionConfig cc, SsrfGuard guard) {
        for (AddressField field : spec.addressFields()) {
            check(field.label(), field.value().apply(cc), guard);
        }
    }

    private static ConnectionConfig connectionConfig(VectorStoreView store) {
        return store.getConnectionConfig() == null ? new ConnectionConfig()
                : store.getConnectionConfig();
    }

    // ── 地址字段 ────────────────────────────────────────────────────────────

    private static AddressField addrField() {
        return new AddressField("vector store address", cc -> cc.addr);
    }

    /** qdrant 的端点：host 与 port 都有值时拼 {@code [host]:port}（去方括号）。 */
    private static String qdrantEndpoint(ConnectionConfig cc) {
        String endpoint = cc.host == null ? "" : cc.host;
        if (!endpoint.isEmpty() && cc.port != 0) {
            endpoint = "[" + stripBrackets(endpoint) + "]:" + cc.port;
        }
        return endpoint;
    }

    // ── 逐引擎构造 ──────────────────────────────────────────────────────────

    private static KeywordsVectorHybridRetrieveEngineService elasticsearch(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // 与其余分支同款判空：connection_config 为 NULL 的 DB-store 行
        // （type handler 可能给 null）在启动/重建期会 NPE
        ConnectionConfig cc = connectionConfig(store);
        IndexConfig idx = store.getIndexConfig();
        String indexName = idx == null ? "" : idx.indexName;
        int shards = idx != null && idx.numberOfShards > 0 ? idx.numberOfShards : 0;
        int replicas = idx != null && idx.numberOfReplicas > 0 ? idx.numberOfReplicas : -1;
        boolean v7 = cc.version != null && cc.version.startsWith("7.");
        RetrieveEngineRepository repo = v7
                ? new ElasticsearchV7RetrieveRepository(cc.addr, indexName, shards,
                        replicas, cc.username, cc.password, guard)
                : new ElasticsearchV8RetrieveRepository(cc.addr, indexName, shards,
                        replicas, cc.username, cc.password, guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_ELASTICSEARCH);
    }

    private static KeywordsVectorHybridRetrieveEngineService postgresUnsupported(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // postgres 的读/写由 PgVectorRetrieveRepository / VectorStoreService（JDBC）承担，不经引擎工厂。
        throw new EngineNotSupportedException(
                "postgres retriever is served by the existing JDBC pieces"
                + " (PgVectorRetrieveRepository / VectorStoreService), not by this"
                + " factory");
    }

    private static KeywordsVectorHybridRetrieveEngineService sqlite(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // **忽略 store 的 connection_config**——SQLite 引擎不按店连接，
        // 介质是一颗独立 SQLite 文件：路径取 connection_config.addr（若像路径）
        // → env SQLITE_PATH → 缺省 ./data/weknora-retrieval.sqlite。
        // 地址免 SSRF（无网络面）。
        ConnectionConfig cc = connectionConfig(store);
        SqliteRetrieveRepository repo = SqliteRetrieveRepository.create(
                SqliteRetrieveRepository.resolvePath(cc.addr));
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_SQLITE);
    }

    private static KeywordsVectorHybridRetrieveEngineService opensearch(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // env-store（前缀 id）折叠为 ""——
        // env store 共享集群、无 per-store 索引前缀；索引名 ≥16
        // 字符规则由驱动强制。探针（版本 + k-NN 插件）在构造期显形。
        ConnectionConfig cc = connectionConfig(store);
        IndexConfig idx = store.getIndexConfig();
        String storeId = EnvStoreIds.isEnvStoreId(store.getId()) ? "" : store.getId();
        OpenSearchRetrieveRepository repo = new OpenSearchRetrieveRepository(
                cc.addr, storeId, idx, cc.username, cc.password,
                cc.insecureSkipVerify, guard);
        if (auditSink != null) {
            repo.withAuditSink(auditSink);
        }
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_OPENSEARCH);
    }

    private static KeywordsVectorHybridRetrieveEngineService doris(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // Addr 承担 host:9030 的 MySQL 端点；
        // HTTPPort + Addr 的 host 部分组成 Stream Load 的 HTTP base（缺省 FE 8030）。
        ConnectionConfig cc = connectionConfig(store);
        String addr = cc.addr == null ? "" : cc.addr;
        if (addr.isEmpty()) {
            throw new EngineNotSupportedException(
                    "doris connection requires addr (host:port)");
        }
        if (cc.database == null || cc.database.isEmpty()) {
            throw new EngineNotSupportedException("doris connection requires database");
        }
        int httpPort = cc.httpPort > 0 ? cc.httpPort : 8030;
        String httpBase = "http://" + DorisRetrieveRepository.hostFromAddr(addr)
                + ":" + httpPort;
        DorisRetrieveRepository repo = DorisRetrieveRepository.create(addr, httpBase,
                cc.username, cc.password, cc.database, store.getIndexConfig(), guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_DORIS);
    }

    private static KeywordsVectorHybridRetrieveEngineService qdrant(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // host（空=localhost 由客户端缺省）、port 缺省 6334、
        // api_key、use_tls；地址策略在 validate 已校验。
        ConnectionConfig cc = connectionConfig(store);
        int port = cc.port == 0 ? 6334 : cc.port;
        QdrantRetrieveRepository repo = QdrantRetrieveRepository.create(cc.host,
                port, cc.apiKey, cc.useTls, store.getIndexConfig(), guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_QDRANT);
    }

    private static KeywordsVectorHybridRetrieveEngineService weaviate(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // host 缺省 weaviate:8080、scheme 缺省 http、
        // api_key 直取（工厂路径不看 WEAVIATE_AUTH_ENABLED）；
        // grpc_address 缺省 weaviate:50051 但本实现走 REST（见驱动类注释）。
        ConnectionConfig cc = connectionConfig(store);
        String weaviateHost = cc.host == null || cc.host.isEmpty()
                ? "weaviate:8080" : cc.host;
        String weaviateScheme = cc.scheme == null || cc.scheme.isEmpty()
                ? "http" : cc.scheme;
        WeaviateRetrieveRepository repo = WeaviateRetrieveRepository.create(weaviateHost,
                weaviateScheme, cc.apiKey, store.getIndexConfig(), guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_WEAVIATE);
    }

    private static KeywordsVectorHybridRetrieveEngineService milvus(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // addr 缺省 localhost:19530；username/password/
        // database 非空才设；本实现走 REST v2（见驱动类注释）。
        ConnectionConfig cc = connectionConfig(store);
        String milvusAddr = cc.addr == null || cc.addr.isEmpty()
                ? "localhost:19530" : cc.addr;
        MilvusRetrieveRepository repo = MilvusRetrieveRepository.create(milvusAddr,
                cc.username, cc.password, cc.database,
                store.getIndexConfig(), guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_MILVUS);
    }

    private static KeywordsVectorHybridRetrieveEngineService tencentVectorDb(
            VectorStoreView store, SsrfGuard guard,
            OpenSearchRetrieveRepository.AuditSink auditSink) {
        // addr 必填（只收 http:// 或裸 host，
        // https 被拒）、username/apiKey 必填（"username or key is empty"）。
        ConnectionConfig cc = connectionConfig(store);
        TencentVectorDbRetrieveRepository repo = TencentVectorDbRetrieveRepository.create(
                cc.addr, cc.username, cc.apiKey, cc.database,
                store.getIndexConfig(), guard);
        return new KeywordsVectorHybridRetrieveEngineService(repo,
                EngineTypes.ENGINE_TENCENT_VECTORDB);
    }

    private static String stripBrackets(String host) {
        String trimmed = host.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void check(String label, String endpoint, SsrfGuard guard) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return;
        }
        if (guard == null) {
            return;
        }
        try {
            guard.validateURLForSSRF(value);
        } catch (RuntimeException e) {
            throw new EngineNotSupportedException(
                    label + " failed SSRF validation: " + e.getMessage());
        }
    }

}
