package com.ragagent.llm.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.contract.model.ChatMessage;
import com.ragagent.contract.model.ChatOptions;
import com.ragagent.contract.model.ChatResponse;
import com.ragagent.contract.model.ChatTool;
import com.ragagent.contract.model.FunctionDef;
import com.ragagent.contract.model.ImageUrl;
import com.ragagent.contract.model.MessageContentPart;
import com.ragagent.contract.model.RankResult;
import com.ragagent.contract.model.StreamResponse;
import com.ragagent.contract.model.ToolCall;
import com.ragagent.platform.web.JsonModuleProvider;

/**
 * 模型面契约载荷的 Jackson 装配（mixin）。
 *
 * <p>经 {@link JsonModuleProvider} 由 {@code JsonMappers} 自动发现：{@code platform} 不必认识
 * 模型面契约类型。凡**绕过 {@code JsonMappers} 直接 new ObjectMapper 的 mapper**，
 * 必须显式 {@code registerModule(new ModelJsonModule())}，否则线格式会退回 Jackson 默认。</p>
 */
public final class ModelJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public ModelJsonModule() {
        super("ragagent-model-json");
        setMixInAnnotation(ChatMessage.class, ContractJsonMixinsModelChat.ChatMessageMixin.class);
        setMixInAnnotation(ChatOptions.class, ContractJsonMixinsModelChat.ChatOptionsMixin.class);
        setMixInAnnotation(ChatResponse.class, ContractJsonMixinsModelChat.ChatResponseMixin.class);
        setMixInAnnotation(ChatTool.class, ContractJsonMixinsModelChat.ChatToolMixin.class);
        setMixInAnnotation(FunctionDef.class, ContractJsonMixinsModelChat.FunctionDefMixin.class);
        setMixInAnnotation(ImageUrl.class, ContractJsonMixinsModelChat.ImageUrlMixin.class);
        setMixInAnnotation(MessageContentPart.class,
                ContractJsonMixinsModelChat.MessageContentPartMixin.class);
        setMixInAnnotation(RankResult.class, ContractJsonMixinsModel.RankResultMixin.class);
        setMixInAnnotation(RankResult.DocumentInfo.class,
                ContractJsonMixinsModel.RankResultMixin.DocumentInfoMixin.class);
        setMixInAnnotation(StreamResponse.class, ContractJsonMixinsModel.StreamResponseMixin.class);
        setMixInAnnotation(ToolCall.class, ContractJsonMixinsModelChat.ToolCallMixin.class);
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new ModelJsonModule();
        }
    }
}
