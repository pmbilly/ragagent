package com.ragagent.common.vectorstore;

/**
 * env 提供的向量库 id 判定（B107）：纯字符串谓词，L2 {@code retrieval} 的引擎工厂要用它
 * 区分"共享集群的 env 库"（无 per-store 索引前缀）与普通库。
 *
 * <p>常量与谓词从 {@code vectorstore.domain.EnvVectorStores} 提取到 L1（那边保留委托），
 * 避免 L2 为一个字符串前缀依赖业务域。</p>
 */
public final class EnvStoreIds {

    /** env 库 id 前缀。 */
    public static final String ENV_STORE_ID_PREFIX = "__env_";

    private EnvStoreIds() {
    }

    public static boolean isEnvStoreId(String id) {
        return id != null && id.startsWith(ENV_STORE_ID_PREFIX);
    }
}
