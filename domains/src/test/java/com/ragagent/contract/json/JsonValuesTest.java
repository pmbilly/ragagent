package com.ragagent.contract.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 契约侧纯 JDK JSON 读面：与 {@code JsonNode} 的语义对齐（G1-k）。
 *
 * <p>守护点：缺键/{@code null} → 默认值；数字/布尔文本化；{@code asInt} 的宽松解析；
 * 非对象/非数组的边界回退。</p>
 */
class JsonValuesTest {

    @Test
    @DisplayName("缺键 / null ⇒ 默认值（对齐 path(k).asText(\"\")）")
    void missingAndNullFallBackToDefault() {
        assertThat(JsonValues.asText(JsonValues.get(Map.of(), "k"), "")).isEmpty();
        assertThat(JsonValues.asText(JsonValues.get(null, "k"), "")).isEmpty();
        assertThat(JsonValues.isNull(JsonValues.get(Map.of(), "k"))).isTrue();
        assertThat(JsonValues.isNull(JsonValues.get(Map.of("k", "v"), "k"))).isFalse();
    }

    @Test
    @DisplayName("标量读面：文本/数字/布尔与 JsonNode 对齐")
    void scalarReads() {
        assertThat(JsonValues.asText("x", "")).isEqualTo("x");
        assertThat(JsonValues.asText(3, "")).isEqualTo("3");
        assertThat(JsonValues.asText(true, "")).isEqualTo("true");
        assertThat(JsonValues.asInt(3, 0)).isEqualTo(3);
        assertThat(JsonValues.asInt(" 42 ", 0)).isEqualTo(42);
        assertThat(JsonValues.asInt("abc", 7)).isEqualTo(7);
        assertThat(JsonValues.asLong(3, 0L)).isEqualTo(3L);
        assertThat(JsonValues.asDouble(1.5, 0)).isEqualTo(1.5);
        assertThat(JsonValues.asBoolean(true, false)).isTrue();
        assertThat(JsonValues.asBoolean("TRUE", false)).isTrue();
        assertThat(JsonValues.asBoolean("yes", true)).isTrue();
    }

    @Test
    @DisplayName("容器读面：elements/fields/size 与非容器边界")
    void containerReads() {
        Object arr = List.of("a", "b");
        assertThat(JsonValues.isArray(arr)).isTrue();
        assertThat(JsonValues.isObject(arr)).isFalse();
        assertThat(JsonValues.size(arr)).isEqualTo(2);
        assertThat(JsonValues.elements(arr)).containsExactly("a", "b");
        assertThat(JsonValues.elements("x")).isEmpty();

        Object obj = Map.of("a", 1);
        assertThat(JsonValues.isObject(obj)).isTrue();
        assertThat(JsonValues.fields(obj)).containsEntry("a", 1);
        assertThat(JsonValues.size(obj)).isEqualTo(1);
        assertThat(JsonValues.fields("x")).isEmpty();
    }

    @Test
    @DisplayName("boolAt：等价 JsonNode#path(k).asBoolean(def)（视图派生访问器用法）")
    void boolAtMirrorsPathAsBoolean() {
        Object cfg = Map.of("enabled", true);
        assertThat(JsonValues.boolAt(cfg, "enabled", false)).isTrue();
        assertThat(JsonValues.boolAt(cfg, "missing", true)).isTrue();
        assertThat(JsonValues.boolAt("not-a-map", "enabled", false)).isFalse();
        assertThat(JsonValues.boolAt(null, "enabled", false)).isFalse();
    }
}
