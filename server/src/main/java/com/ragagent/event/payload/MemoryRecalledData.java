package com.ragagent.event.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 本轮注入的长期记忆。
 *
 * <p>零值输出 {@code {"memories":null}}（恒输出）。
 * 为与本包外领域类型解耦，元素以 Object 承载。</p>
 */

public class MemoryRecalledData {

    /** null 也输出 null */
    @JsonProperty("memories")
    private Object memories;

    public MemoryRecalledData() {
    }

    public MemoryRecalledData(Object memories) {
        this.memories = memories;
    }

    public Object getMemories() {
        return memories;
    }

    public void setMemories(Object v) {
        this.memories = v;
    }
}
