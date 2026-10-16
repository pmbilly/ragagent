package com.ragagent.contract.json;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 零值时间哨兵（{@code "0001-01-01T00:00:00Z"}）——落 jsonb / 作响应体的时间字段共用语义的单一实现。
 *
 * <p>原在 {@code platform.web.ZeroTimeSerializer}（该序列化器保留既有常量名并委托此处）；
 * 契约载荷的默认值与判据（如 {@code AgentStep#timestamp}、{@code MemoryMessageCursor#at}）引用本类。</p>
 */
public final class ZeroTimes {

    /** 零值时间的瞬时（year 1 元旦 UTC）。 */
    public static final Instant ZERO_INSTANT = Instant.parse("0001-01-01T00:00:00Z");

    /** 零值的字面输出。 */
    public static final String ZERO_LITERAL = "0001-01-01T00:00:00Z";

    /**
     * 零值时间的 Java 表示——供字段**默认值**使用。
     *
     * <p>⚠️ Jackson 对 {@code null} 值调用的是 {@code nullSerializer}，<b>不会</b>走
     * {@code @JsonSerialize(using=…)} 指定的序列化器 ⇒ "字段为 null 时输出 year-1 字面量"
     * 行不通；必须让字段本身就持有零值时间（贴合"时间非空"的既定语义）。</p>
     */
    public static final OffsetDateTime ZERO_DATE_TIME =
            OffsetDateTime.ofInstant(ZERO_INSTANT, ZoneOffset.UTC);

    /** 该值是否就是零值时间（null 视为零值）。 */
    public static boolean isZero(OffsetDateTime value) {
        return value == null || ZERO_INSTANT.equals(value.toInstant());
    }

    private ZeroTimes() {
    }
}
