package com.ragagent.common.knowledge;

/**
 * 文本 chunk 只读视图（B98/C2）。
 *
 * <p>字段取 <b>消费方实际读取</b> 的投影（正文重建、引用分批、图片富化、清理）：
 * {@code id} / {@code content} / {@code chunkType} / {@code chunkIndex} /
 * {@code startAt} / {@code endAt}——排序与重叠去重由 wiki 的 {@code WikiChunkMerge} 负责。
 * 见 {@code common/knowledge/package-info.java}：<b>需要更多字段时先改载荷，别把实体漏出去</b>。</p>
 *
 * <p><b>可写说明</b>：DTO——{@code knowledge} 侧适配器填充、测试夹具构造；
 * 业务调用方应只读使用。</p>
 */
public final class ChunkView {

    private String id;
    private String content;
    private String chunkType;
    private int chunkIndex;
    private int startAt;
    private int endAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getStartAt() {
        return startAt;
    }

    public void setStartAt(int startAt) {
        this.startAt = startAt;
    }

    public int getEndAt() {
        return endAt;
    }

    public void setEndAt(int endAt) {
        this.endAt = endAt;
    }
}
