package com.ragagent.config.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.auth.api.ChatHistoryConfig;
import com.ragagent.auth.api.ParserEngineConfig;
import com.ragagent.auth.api.RetrievalConfig;
import com.ragagent.auth.api.UserPreferences;
import com.ragagent.auth.api.WebSearchConfig;
import com.ragagent.knowledge.api.wiki.ExtractedItem;
import com.ragagent.knowledge.api.wiki.SlugUpdate;
import com.ragagent.memory.api.MemoryMessageCursor;
import com.ragagent.platform.web.JsonModuleProvider;
import com.ragagent.retrieval.api.ImageInfo;
import com.ragagent.shared.session.MessageView;
import com.ragagent.shared.tenant.APIPrincipalConfig;
import com.ragagent.shared.tenant.StorageEngineConfig;

/**
 * 业务域面载荷的 Jackson 装配（wiki / tenant 配置 / 会话 / 记忆 / 认证 / 网页搜索）。
 *
 * <p>经 {@link JsonModuleProvider} 由 {@code JsonMappers} 自动发现：framework 不必认识
 * 这些域类型，线格式由 app 自己声明。</p>
 */
public final class AppJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public AppJsonModule() {
        super("ragagent-app-json");
        setMixInAnnotation(ExtractedItem.class, AppJsonMixins.ExtractedItemMixin.class);
        setMixInAnnotation(SlugUpdate.class, AppJsonMixins.SlugUpdateMixin.class);
        setMixInAnnotation(WebSearchConfig.class, AppJsonMixins.WebSearchConfigMixin.class);
        setMixInAnnotation(ParserEngineConfig.class, AppJsonMixins.ParserEngineConfigMixin.class);
        setMixInAnnotation(APIPrincipalConfig.class, TenantJsonMixins.APIPrincipalConfigMixin.class);
        setMixInAnnotation(ChatHistoryConfig.class, TenantJsonMixins.ChatHistoryConfigMixin.class);
        setMixInAnnotation(RetrievalConfig.class, TenantJsonMixins.RetrievalConfigMixin.class);
        setMixInAnnotation(StorageEngineConfig.class, TenantJsonMixins.StorageEngineConfigMixin.class);
        setMixInAnnotation(ImageInfo.class, WebSearchJsonMixins.ImageInfoMixin.class);
        setMixInAnnotation(MemoryMessageCursor.class, MemoryJsonMixins.MemoryMessageCursorMixin.class);
        setMixInAnnotation(MessageView.class, SessionJsonMixins.MessageViewMixin.class);
        setMixInAnnotation(UserPreferences.class, AuthJsonMixins.UserPreferencesMixin.class);
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new AppJsonModule();
        }
    }
}
