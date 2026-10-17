package com.ragagent.contract.memory;

import com.ragagent.contract.json.ZeroTimes;
import java.time.OffsetDateTime;

/**
 * 抽取游标：用消息主键**打破时间戳平局**。
 *
 * <p>纯值对象，不落表。它出现在记忆抽取会话行 {@code getCursor()} 的嵌套 JSON 里
 * （{@code MemoryExtractionSession}，留记忆域），也作为 {@code failed_from_} /
 * {@code failed_to_} 的 {@code embeddedPrefix} 展开成平列。</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 线格式注解由
 * {@code platform.web.ContractJsonMixinsMemory} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class MemoryMessageCursor {

    private OffsetDateTime at = ZeroTimes.ZERO_DATE_TIME;

    private String id = "";

    public MemoryMessageCursor() {
    }

    public MemoryMessageCursor(OffsetDateTime at, String id) {
        // 直接字段归一赋值（不经 setter）：setter 非 final 可被子类覆写，
        // 构造器里调用会让 this 在子类构造完成前逃逸（-Xlint:this-escape）
        this.at = at == null ? ZeroTimes.ZERO_DATE_TIME : at;
        this.id = id == null ? "" : id;
    }

    /**
     * 排序比较：先比 {@code at}，相等时用 {@code id} 打破平局。
     *
     * <p>方法名刻意不带 {@code is}/{@code get} 前缀——Jackson 不会把它当属性（§7.5 第 2 条）。</p>
     */
    public boolean after(MemoryMessageCursor other) {
        if (other == null) {
            return true;
        }
        return at.isAfter(other.at) || (at.isEqual(other.at) && id.compareTo(other.id) > 0);
    }

    /**
     * {@code at} 与给定时刻是否同一瞬间（按 instant 相等比较）。
     *
     * <p>名字**刻意**不用 {@code isXxx} 形式：那正是 §7.5 第 2 条的坑
     * （Jackson 会把零参 {@code isXxx()} 当属性名 {@code xxx} 写出去）。
     * 带参数的方法 Jackson 本来也不认，但少一个可疑形状少一分复发率。</p>
     */
    public boolean atEquals(OffsetDateTime other) {
        return other != null && at.toInstant().equals(other.toInstant());
    }

    public OffsetDateTime getAt() { return at; }
    public void setAt(OffsetDateTime v) {
        at = v == null ? ZeroTimes.ZERO_DATE_TIME : v;
    }

    public String getId() { return id; }
    public void setId(String v) { id = v == null ? "" : v; }
}
