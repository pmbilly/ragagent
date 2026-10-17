package com.ragagent.config;

import com.ragagent.contract.model.PromptCacheStatus;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.platform.web.ContractJsonMixins;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Boot 4 过渡期 Jackson 3 契约模块：复用 Jackson 2 的 mixin 映射表，
 * 补上枚举的字符串 ser/deser。Jackson 3 本身认 {@code com.fasterxml.jackson.annotation}，
 * 所以 mixin 类无需重写；迁移完成后可整体收敛为唯一的 Jackson 模块。
 */
final class ContractJackson3Module extends SimpleModule {

    ContractJackson3Module() {
        super("ragagent-contract-json3");
        ContractJsonMixins.mappings().forEach(this::setMixInAnnotation);
        addSerializer(ResponseType.class, new ValueSerializer<ResponseType>() {
            @Override
            public void serialize(ResponseType value, JsonGenerator gen, SerializationContext ctxt)
                    throws JacksonException {
                gen.writeString(value.value());
            }
        });
        addDeserializer(ResponseType.class, new ValueDeserializer<ResponseType>() {
            @Override
            public ResponseType deserialize(JsonParser p, DeserializationContext ctxt)
                    throws JacksonException {
                return ResponseType.fromValue(p.getString());
            }
        });
        addSerializer(PromptCacheStatus.class, new ValueSerializer<PromptCacheStatus>() {
            @Override
            public void serialize(PromptCacheStatus value, JsonGenerator gen, SerializationContext ctxt)
                    throws JacksonException {
                gen.writeString(value.value());
            }
        });
        addDeserializer(PromptCacheStatus.class, new ValueDeserializer<PromptCacheStatus>() {
            @Override
            public PromptCacheStatus deserialize(JsonParser p, DeserializationContext ctxt)
                    throws JacksonException {
                return PromptCacheStatus.fromValue(p.getString());
            }
        });
    }
}
