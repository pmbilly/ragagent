package com.ragagent.common.memory;

import java.time.OffsetDateTime;

/**
 * 一条被查到的记忆条目（工具面载荷）：字段与会话侧工具视图一一对应，
 * 空值语义原样传递（零值归一由调用方按其视图规则处理）。
 */
public record MemorySearchHit(String kind, String topic, String content, OffsetDateTime validFrom) {
}
