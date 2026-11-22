package com.ragagent.llm.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.framework.jsonbind.JsonModuleProvider;
import com.ragagent.llm.api.ChatMessage;
import com.ragagent.llm.api.ChatOptions;
import com.ragagent.llm.api.ChatResponse;
import com.ragagent.llm.api.ChatTool;
import com.ragagent.llm.api.FunctionDef;
import com.ragagent.llm.api.ImageUrl;
import com.ragagent.llm.api.MessageContentPart;
import com.ragagent.llm.api.PromptCacheStatus;
import com.ragagent.llm.api.RankResult;
import com.ragagent.llm.api.ResponseType;
import com.ragagent.llm.api.StreamResponse;
import com.ragagent.llm.api.TokenUsage;
import com.ragagent.llm.api.ToolCall;
import java.io.IOException;

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
        setMixInAnnotation(TokenUsage.class, ContractJsonMixinsModel.TokenUsageMixin.class);
        setMixInAnnotation(ToolCall.class, ContractJsonMixinsModelChat.ToolCallMixin.class);
        addSerializer(ResponseType.class, new ResponseTypeSerializer());
        addDeserializer(ResponseType.class, new ResponseTypeDeserializer());
        addSerializer(PromptCacheStatus.class, new PromptCacheStatusSerializer());
        addDeserializer(PromptCacheStatus.class, new PromptCacheStatusDeserializer());
    }

    private static final class ResponseTypeSerializer
            extends JsonSerializer<ResponseType> {
        @Override
        public void serialize(ResponseType value,
                              JsonGenerator gen,
                              SerializerProvider provider)
                throws IOException {
            gen.writeString(value.value());
        }
    }

    private static final class ResponseTypeDeserializer
            extends JsonDeserializer<ResponseType> {
        @Override
        public ResponseType deserialize(JsonParser p,
                                        DeserializationContext ctxt)
                throws IOException {
            return ResponseType.fromValue(p.getText());
        }
    }

    private static final class PromptCacheStatusSerializer
            extends JsonSerializer<PromptCacheStatus> {
        @Override
        public void serialize(PromptCacheStatus value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    private static final class PromptCacheStatusDeserializer
            extends JsonDeserializer<PromptCacheStatus> {
        @Override
        public PromptCacheStatus deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            return PromptCacheStatus.fromValue(p.getText());
        }
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new ModelJsonModule();
        }
    }
}
