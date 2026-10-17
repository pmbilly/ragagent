package com.ragagent.platform.stream;

import com.ragagent.platform.web.ContractJsonModule;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * 流事件进出 Redis 用的 ObjectMapper（**不是** HTTP 响应那个）。
 *
 * <p>本 mapper 与 HTTP 响应那个的差异，以下两处保留：</p>
 * <ol>
 *   <li><b>map 按键字母序</b>：{@code UpdateSteerEventData} 的 CAS 把**读到的原文**
 *       与 LSET 前的槽位比对——同一 data 必须序列化出<b>稳定字节</b>，键序不能随机。</li>
 *   <li><b>timestamp 用本地时区 + ISO_OFFSET_DATE_TIME</b>：与
 *       {@code config.JacksonConfig} 对 OffsetDateTime 的处置一致（RFC3339Nano，
 *       纳秒尾部零裁剪）。该覆盖在 JavaTimeModule
 *       之后注册（后者后注册者胜），由 {@code StreamJsonTest} 钉住。</li>
 *   <li><b>容忍未知属性</b>：Jackson 默认失败，这里关闭——旧版本写下的行不能因为多了个字段就整条读不出来。</li>
 * </ol>
 */
public final class StreamJson {

    private static final ObjectMapper MAPPER = build();

    private static ObjectMapper build() {
        ObjectMapper mapper = JsonMapper.builder().disable(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .addModule(new ContractJsonModule())
                .addModule(offsetDateTimeModule())
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return mapper;
    }

    private StreamJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException e) {
            throw new StreamStoreException("failed to marshal event: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化单个字符串（连引号的 JSON 字面量）。
     *
     * <p>{@code ClearLiveRun} 的 CAS 要在原始 JSON 里做子串匹配
     * （{@code "assistant_message_id":<这里>}），所以引号与转义必须由同一个
     * mapper 产出，不能手工拼 {@code "\"" + id + "\""}。</p>
     */
    public static String writeString(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException e) {
            throw new StreamStoreException("failed to marshal assistant message id: " + e.getMessage(), e);
        }
    }

    /** 反序列化失败时抛 {@link StreamStoreException}，消息即 Jackson 的原始描述——由调用方加前缀。 */
    public static <T> T read(String raw, Class<T> type) {
        try {
            return MAPPER.readValue(raw, type);
        } catch (tools.jackson.core.JacksonException e) {
            throw new StreamStoreException(String.valueOf(e.getMessage()), e);
        }
    }

    /** 与 {@code config.JacksonConfig} 同一套 OffsetDateTime 输出规则（本地时区 + RFC3339Nano）。 */
    private static JacksonModule offsetDateTimeModule() {
        return new SimpleModule().addSerializer(OffsetDateTime.class, new ValueSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializationContext serializers) {
                OffsetDateTime local = value.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
                gen.writeString(local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
    }
}
