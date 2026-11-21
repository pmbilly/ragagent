package com.ragagent.retrieval.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.platform.web.JsonModuleProvider;
import com.ragagent.retrieval.api.ConnectionConfig;
import com.ragagent.retrieval.api.IndexConfig;
import com.ragagent.retrieval.api.SearchParams;
import com.ragagent.shared.retrieval.SearchResult;

/**
 * 检索与向量库面载荷的 Jackson 装配。
 *
 * <p>经 {@link JsonModuleProvider} 由 {@code JsonMappers} 自动发现：framework 不必认识
 * 检索面载荷类型，线格式由本模块自己声明。</p>
 */
public final class RetrievalJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public RetrievalJsonModule() {
        super("ragagent-retrieval-json");
        setMixInAnnotation(ConnectionConfig.class, VectorJsonMixins.ConnectionConfigMixin.class);
        setMixInAnnotation(IndexConfig.class, VectorJsonMixins.IndexConfigMixin.class);
        setMixInAnnotation(SearchParams.class, VectorJsonMixins.SearchParamsMixin.class);
        setMixInAnnotation(SearchResult.class, RetrievalJsonMixins.SearchResultMixin.class);
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new RetrievalJsonModule();
        }
    }
}
