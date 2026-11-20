package com.ragagent.knowledge.controller.vo;

/**
 * {@code ChunkUpdateResponse} 的 HTTP 响应体。
 *
 * <p>字段声明顺序即线格式：Jackson 按 record 组件顺序输出。</p>
 */
public record ChunkUpdateRespVo(
        ChunkRespVo chunk,
        String description,
        String summaryStatus) {
}
