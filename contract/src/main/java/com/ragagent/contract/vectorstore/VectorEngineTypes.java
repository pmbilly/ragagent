package com.ragagent.contract.vectorstore;

import java.util.Set;

/**
 * 向量 / 检索引擎类型的**唯一常量来源**。
 *
 * <p>运行时工厂（{@code retrieval-engine} 的 {@code EngineTypes}）与域侧白名单
 * （{@code vectorstore} 的 {@code VectorStoreEngines}）都从这里派生：新增一种引擎只改本类，
 * 并明确它是否允许注册为 DB 向量库（{@link #CONFIGURABLE}）。</p>
 *
 * <p>postgres / sqlite 是环境引擎——只能经 {@code RETRIEVE_DRIVER} 的 env store 出现，
 * 不能作为 DB 里的 {@code vector_stores} 行注册，故不在 {@link #CONFIGURABLE} 内。</p>
 */
public final class VectorEngineTypes {

    private VectorEngineTypes() {
    }

    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String POSTGRES = "postgres";
    public static final String SQLITE = "sqlite";
    public static final String OPENSEARCH = "opensearch";
    public static final String DORIS = "doris";
    public static final String QDRANT = "qdrant";
    public static final String WEAVIATE = "weaviate";
    public static final String MILVUS = "milvus";
    public static final String TENCENT_VECTORDB = "tencent_vectordb";

    /** 全部引擎类型（含只能经 env store 出现的 postgres / sqlite）。 */
    public static final Set<String> ALL = Set.of(
            ELASTICSEARCH, POSTGRES, SQLITE, OPENSEARCH, DORIS,
            QDRANT, WEAVIATE, MILVUS, TENCENT_VECTORDB);

    /** 可注册为 DB 向量库的类型（env 引擎除外）。 */
    public static final Set<String> CONFIGURABLE = Set.of(
            ELASTICSEARCH, OPENSEARCH, DORIS, QDRANT, WEAVIATE, MILVUS, TENCENT_VECTORDB);
}
