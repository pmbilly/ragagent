package com.ragagent.datasource.domain;

import com.ragagent.platform.web.SortedMapSerializer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.util.RawValue;

/**
 * {@link SortedMapSerializer} 的 datasource 版本：在"按键排序且递归"之上，**再把嵌套的
 * {@code Double} 按 {@code Double.toString} 语义输出。
 *
 * <h2>为什么不能直接用 {@code SortedMapSerializer}</h2>
 * <p>{@code SortedMapSerializer} 只重排键序，值<b>原样</b>交给 Jackson。而本模块的 map 字段
 * （{@code DataSourceConfig.settings} / {@code Resource.metadata} /
 * {@code SyncCursor.connector_cursor}）装的是**外部系统给的任意 JSON**，里面必然有数字：
 * 分页偏移、条目上限、文档大小。嵌套数字的文本形态统一按 {@code Double.toString}
 * 语义输出：</p>
 * <pre>
 *   Double(1.0)    →  1.0
 *   Double(1e21)   →  1.0E21
 * </pre>
 * <p>{@code Resource.metadata} 是<b>响应体</b>、{@code connector_cursor} 会经
 * {@code last_sync_result} 原样透给前端——分叉看得见。</p>
 *
 * <h2>为什么不改 {@code SortedMapSerializer} 本身</h2>
 * <p>它是被 MCP / Wiki / stream / llm 共同依赖的<b>共享</b>基础设施，
 * 不宜为单个模块改动。故在此处收口：本类继承它、复用它的键序比较器
 * 与 {@code sortDeep}，只补一层"值归一"。</p>
 *
 * <h2>归一的手段</h2>
 * <p>把 {@code Double}/{@code Float} 换成 {@link tools.jackson.databind.util.RawValue}。
 * {@code sortDeep} 对非 Map/Collection 的值原样透传，Jackson 会调 RawValue 自带的
 * 序列化器——于是嵌套在任意深度的数字都能走同一套编码器。用 {@code writeRawValue} 而不是
 * {@code writeNumber} 是因为 {@code 1e+21} 这类输出不是合法的 Java 数字字面量写法，
 * 只能原样写出。</p>
 */
public class DataSourceMapSerializer extends SortedMapSerializer {

    @Override
    public void serialize(Map<String, Object> value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // 先归一（Double → RawNumber），再交给父类的 sortDeep 排序。
        // 顺序不能反：sortDeep 会把 map 换成 TreeMap，之后仍然要递归一遍值。
        gen.writePOJO(sortDeep(normalize(value)));
    }

    /** 递归把数字归一为 {@code Double.toString} 文本；map / 集合的容器结构原样保留（排序交给 {@code sortDeep}）。 */
    private static Object normalize(Object value) {
        if (value instanceof Double d) {
            return new RawValue(Double.toString(d));
        }
        if (value instanceof Float f) {
            return new RawValue(Double.toString(f.doubleValue()));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> out = new java.util.ArrayList<>(collection.size());
            for (Object item : collection) {
                out.add(normalize(item));
            }
            return out;
        }
        return value;
    }
}
