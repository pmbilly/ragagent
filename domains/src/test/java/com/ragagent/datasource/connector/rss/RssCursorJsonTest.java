package com.ragagent.datasource.connector.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ragagent.datasource.domain.SyncCursor;
import org.junit.jupiter.api.Test;

/**
 * {@link RssCursor} 的 **jsonb 存储契约**测试。
 *
 * <h2>为什么这个类型要逐字节比</h2>
 * <p>它被塞进 {@code SyncCursor.connector_cursor}、落 {@code data_sources.last_sync_cursor}
 * 这个 jsonb 列。已落库的旧数据要能被当前代码读回来——所以键名、键的出现与否、
 * map 的键序都是契约本身，不是风格问题。</p>
 *
 * <h2>序列化行为（期望值）</h2>
 * <pre>
 *   lastSyncTime=t, 两个 map 都为空                     → {"lastSyncTime":"2006-01-02T15:04:05Z"}
 *                                                        （feedItems/feedSignals 两个键不输出）
 *   两个 map 为 null                                    → 同上
 *   {feedItems:{"https://a/f":{"guid-1":"h:abc",...}},
 *    feedSignals:{"https://a/f":{...}}}                → {"lastSyncTime":...,
 *                                                         "feedItems":{...},"feedSignals":{...}}
 *   {feedItems:{"z":{}}, feedSignals:{"z":{}}}          → 两个键**保留**（值是空 map 但键在）
 * </pre>
 * <p>真实同步产出的一整条 {@code SyncCursor} 示例：
 * {@code {"lastSyncTime":"2026-09-18T06:09:08.504305Z","connector_cursor":{"feedItems":{…},
 * "feedSignals":{…},"lastSyncTime":"2026-09-18T06:09:08.504305Z"},"last_schema_hash":""}}
 * ——注意 {@code connectorCursor} 里的键是<b>字母序</b>（{@code feedItems} &lt;
 * {@code feedSignals} &lt; {@code lastSyncTime}），
 * 由 {@code DataSourceMapSerializer} 保证。</p>
 *
 * <h2>时间的时区写法</h2>
 * <p>{@code lastSyncTime} 按标准 ISO-8601 输出 UTC（{@code "…Z"}）。
 * {@link #lastSyncTimeIsSameInstantAndUtcLikeGo()} 把这条表示显式钉住。</p>
 */
class RssCursorJsonTest {

    private static final Pattern KEY = Pattern.compile("\"([A-Za-z_][A-Za-z0-9_]*)\":");

    private static OffsetDateTime t() {
        return OffsetDateTime.of(2006, 1, 2, 15, 4, 5, 0, ZoneOffset.UTC);
    }

    private static RssCursor cursor(OffsetDateTime lastSync,
                                    Map<String, Map<String, String>> items,
                                    Map<String, Map<String, String>> signals) {
        RssCursor c = new RssCursor();
        c.setLastSyncTime(lastSync);
        c.setFeedItems(items);
        c.setFeedSignals(signals);
        return c;
    }

    @Test
    void omitsEmptyFeedMapsButKeepsLastSyncTime() {
        // 空 map / null map 都只输出 lastSyncTime
        Map<String, Object> emptyMaps = cursor(t(), new LinkedHashMap<>(), new LinkedHashMap<>())
                .toMap();
        assertThat(emptyMaps).containsOnlyKeys("lastSyncTime");

        Map<String, Object> nilMaps = cursor(t(), null, null).toMap();
        assertThat(nilMaps).containsOnlyKeys("lastSyncTime");
    }

    @Test
    void emitsBothMapsWhenPopulated() {
        Map<String, Map<String, String>> items = new LinkedHashMap<>();
        items.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "h:abc", "guid-2", "h:def")));
        Map<String, Map<String, String>> signals = new LinkedHashMap<>();
        signals.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "s:11")));

        Map<String, Object> map = cursor(t(), items, signals).toMap();
        assertThat(map).containsOnlyKeys("lastSyncTime", "feedItems", "feedSignals");

        @SuppressWarnings("unchecked")
        Map<String, Object> feedItems = (Map<String, Object>) map.get("feedItems");
        assertThat(feedItems).containsOnlyKeys("https://a/f");
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) feedItems.get("https://a/f");
        assertThat(inner).containsExactlyInAnyOrderEntriesOf(
                Map.of("guid-1", "h:abc", "guid-2", "h:def"));
    }

    @Test
    void keepsFeedKeyWhenInnerMapIsEmpty() {
        // {feedItems:{"z":{}}} → "feedItems":{"z":{}} —— 键的省略只看外层 map 的长度
        Map<String, Map<String, String>> items = new LinkedHashMap<>();
        items.put("z", new LinkedHashMap<>());
        Map<String, Map<String, String>> signals = new LinkedHashMap<>();
        signals.put("z", new LinkedHashMap<>());
        Map<String, Object> map = cursor(t(), items, signals).toMap();
        assertThat(map).containsOnlyKeys("lastSyncTime", "feedItems", "feedSignals");
        // outer key 保留、"内层是空 map"也保留（省略与否只看外层长度）
        assertThat(map.get("feedItems").toString()).isEqualTo("{z={}}");
    }

    @Test
    void syncCursorSerializesConnectorCursorWithAlphabeticalKeys() {
        Map<String, Map<String, String>> items = new LinkedHashMap<>();
        items.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "h:abc")));
        Map<String, Map<String, String>> signals = new LinkedHashMap<>();
        signals.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "s:11")));
        RssCursor rss = cursor(t(), items, signals);

        SyncCursor sync = new SyncCursor();
        sync.setLastSyncTime(rss.getLastSyncTime());
        sync.setConnectorCursor(rss.toMap());

        String json = sync.toJSON().toString();
        // 键序：外层 SyncCursor 键名＝字段名；connectorCursor 内层是 rss 私有键（不动），
        // 且内层 map 恒按字母序输出（DataSourceMapSerializer）
        assertThat(keyOrder(json)).containsExactly(
                "lastSyncTime", "connectorCursor", "feedItems", "feedSignals",
                "lastSyncTime", "lastSchemaHash");
        assertThat(json).contains("\"lastSchemaHash\":\"\"");
        assertThat(json).contains("\"feedItems\"");
        assertThat(json).contains("\"guid-1\":\"h:abc\"");
    }

    @Test
    void lastSyncTimeIsSameInstantAndUtcLikeGo() {
        Map<String, Object> map = cursor(t(), null, null).toMap();
        String rendered = (String) map.get("lastSyncTime");
        // 瞬时一致：这是跨语言能互相读回的关键
        assertThat(Instant.parse(rendered)).isEqualTo(t().toInstant());
        // 按标准 ISO-8601 输出 UTC。
        assertThat(rendered).isEqualTo("2006-01-02T15:04:05Z");
    }

    @Test
    void roundTripsThroughTheStoredMap() {
        Map<String, Map<String, String>> items = new LinkedHashMap<>();
        items.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "h:abc")));
        Map<String, Map<String, String>> signals = new LinkedHashMap<>();
        signals.put("https://a/f", new LinkedHashMap<>(Map.of("guid-1", "s:11")));
        RssCursor original = cursor(t(), items, signals);

        RssCursor restored = RssCursor.fromMap(original.toMap());
        assertThat(restored.getLastSyncTime().toInstant()).isEqualTo(t().toInstant());
        assertThat(restored.getFeedItems()).isEqualTo(items);
        assertThat(restored.getFeedSignals()).isEqualTo(signals);
    }

    @Test
    void toleratesUnknownKeysOnRead() {
        // 读路径必须容忍未知键：旧数据里可能多出将来新增的字段。
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("lastSyncTime", "2006-01-02T15:04:05Z");
        stored.put("feedItems", new LinkedHashMap<String, Object>());
        stored.put("a_future_key", "ignored");
        RssCursor restored = RssCursor.fromMap(stored);
        assertThat(restored).isNotNull();
        assertThat(restored.getLastSyncTime().toInstant()).isEqualTo(t().toInstant());
    }

    @Test
    void fromMapReturnsNullForNullInput() {
        assertThat(RssCursor.fromMap(null)).isNull();
    }

    @Test
    void bucketsAreCreatedOnDemand() {
        RssCursor c = new RssCursor();
        c.bucketForItems("f1").put("i1", "h1");
        c.bucketForSignals("f1").put("i1", "s1");
        c.bucketForItems("f1").put("i2", "h2");
        assertThat(c.getFeedItems().get("f1")).containsEntry("i1", "h1").containsEntry("i2", "h2");
        assertThat(c.getFeedSignals().get("f1")).containsEntry("i1", "s1");
    }

    private static List<String> keyOrder(String json) {
        Matcher m = KEY.matcher(json);
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }
}
