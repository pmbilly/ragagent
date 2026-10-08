package com.ragagent.common.vectorstore;

/**
 * 向量库定义只读视图（B107）。
 *
 * <p>字段取 L2 {@code retrieval} 的实际读取面（引擎工厂与注册表）：
 * {@code id} / {@code tenantId} / {@code name} / {@code engineType} +
 * 两个配置值对象（{@link ConnectionConfig}、{@link IndexConfig}，同属 L1）。
 * 实体上的时间戳/软删标记等管理字段不出域。</p>
 */
public final class VectorStoreView {

    private final String id;
    private final long tenantId;
    private final String name;
    private final String engineType;
    private final ConnectionConfig connectionConfig;
    private final IndexConfig indexConfig;

    public VectorStoreView(String id, long tenantId, String name, String engineType,
                           ConnectionConfig connectionConfig, IndexConfig indexConfig) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.engineType = engineType;
        this.connectionConfig = connectionConfig;
        this.indexConfig = indexConfig;
    }

    public String getId() {
        return id;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getEngineType() {
        return engineType;
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public IndexConfig getIndexConfig() {
        return indexConfig;
    }
}
