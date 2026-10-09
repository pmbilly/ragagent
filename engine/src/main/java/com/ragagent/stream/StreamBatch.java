package com.ragagent.stream;

import java.util.List;

/**
 * 一次增量读的结果（事件列表 + 下一 offset）。
 *
 * @param events     从 fromOffset 起的事件；无新事件时是**空列表**
 * @param nextOffset 下次调用应传入的 offset。注意：解析失败被跳过的事件**仍计入** offset
 *                   （按 Redis 原始条数推进）
 */
public record StreamBatch(List<StreamEvent> events, int nextOffset) {

    public static StreamBatch empty(int fromOffset) {
        return new StreamBatch(List.of(), fromOffset);
    }
}
