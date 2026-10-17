package com.ragagent.platform.web;

import com.ragagent.contract.model.PromptCacheStatus;
import com.ragagent.contract.model.ResponseType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 契约载荷的 Jackson 装配：把线格式（键名/省略策略/枚举字面量）从契约类里剥离到 mixin 与
 * 显式 ser/deser，使 {@code contract.*} 保持纯 JDK。**所有**读写这些载荷的 mapper 都必须注册本模块，
 * 否则键名与省略策略会回退成 Jackson 默认（= 线格式静默变化）。
 *
 * <p>注册点：{@link JsonMappers#lenient()}（jsonb 读路径）、{@code platform.event.EventJson}（事件）、
 * {@code platform.stream.StreamJson}（SSE 流）、{@code modelcontext.JsonValues}（上下文预算）、
 * boot 的 Spring MVC mapper、Langfuse 上报 mapper。</p>
 */
public final class ContractJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /** 构造即完成全部注册（幂等：一次装配一份）。 */
    public ContractJsonModule() {
        super("ragagent-contract-json");
        ContractJsonMixins.mappings().forEach(this::setMixInAnnotation);

        addSerializer(OffsetDateTime.class, new ValueSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value));
            }
        });
        addSerializer(ResponseType.class, new ResponseTypeSerializer());
        addDeserializer(ResponseType.class, new ResponseTypeDeserializer());
        addSerializer(PromptCacheStatus.class, new PromptCacheStatusSerializer());
        addDeserializer(PromptCacheStatus.class, new PromptCacheStatusDeserializer());
    }

    private static final class ResponseTypeSerializer extends ValueSerializer<ResponseType> {
        @Override
        public void serialize(ResponseType v, JsonGenerator g, SerializationContext p) {
            g.writeString(v.value());
        }
    }

    private static final class ResponseTypeDeserializer extends ValueDeserializer<ResponseType> {
        @Override
        public ResponseType deserialize(JsonParser p, DeserializationContext c) {
            return ResponseType.fromValue(p.getString());
        }
    }

    private static final class PromptCacheStatusSerializer extends ValueSerializer<PromptCacheStatus> {
        @Override
        public void serialize(PromptCacheStatus v, JsonGenerator g, SerializationContext p) {
            g.writeString(v.value());
        }
    }

    private static final class PromptCacheStatusDeserializer extends ValueDeserializer<PromptCacheStatus> {
        @Override
        public PromptCacheStatus deserialize(JsonParser p, DeserializationContext c) {
            return PromptCacheStatus.fromValue(p.getString());
        }
    }
}
