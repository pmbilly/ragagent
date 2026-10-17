package com.ragagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.platform.web.JsonMappers;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Boot 4 过渡桥：Jackson 3 的 HTTP mapper 不认 Jackson 2 的 {@link JsonNode}，
 * 默认会把它当成普通 POJO 展开成 array/boolean/... 字段。这里按原始 JSON 写回，
 * 保证未迁移的 controller 仍输出既有线格式；Jackson 3 迁移完成后删除。
 */
final class Jackson2JsonNodeModule extends SimpleModule {

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 =
            JsonMappers.lenient();

    Jackson2JsonNodeModule() {
        addSerializer(JsonNode.class, new ValueSerializer<JsonNode>() {
            @Override
            public void serialize(JsonNode value, JsonGenerator gen, SerializationContext ctxt)
                    throws JacksonException {
                gen.writeRawValue(value.toString());
            }
        });
        addDeserializer(JsonNode.class, new ValueDeserializer<JsonNode>() {
            @Override
            public JsonNode deserialize(JsonParser p, DeserializationContext ctxt)
                    throws JacksonException {
                tools.jackson.databind.JsonNode tree = p.readValueAsTree();
                try {
                    return JACKSON2.readTree(tree.toString());
                } catch (Exception e) {
                    throw tools.jackson.databind.DatabindException.from(
                            p, "failed to bridge Jackson 3 tree to Jackson 2 JsonNode", e);
                }
            }
        });
    }
}
