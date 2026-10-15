package com.ragagent.common.memory;

import java.util.List;

/**
 * 按需查找记忆库的返回（工具面）：{@code available=false} 表示"记忆被关掉了"，
 * 与"没存过匹配的东西"（空条目）是两种必须可区分的结果。
 *
 * @param available 记忆是否可用：工作区、用户、当前请求的 agent 三层任一可关。
 * @param items     匹配结果，最相关在前；不可用时为 null。
 */
public record MemorySearchHits(boolean available, List<MemorySearchHit> items) {

    /** 不可用零值：不可用 + null 条目（口径与域内同名记录一致）。 */
    public static final MemorySearchHits UNAVAILABLE = new MemorySearchHits(false, null);
}
