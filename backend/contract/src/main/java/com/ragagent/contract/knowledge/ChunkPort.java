package com.ragagent.contract.knowledge;

import java.util.List;

/**
 * 知识库 · 文本分块读取与清理端口（wiki 摄取消费）。
 *
 * <p><b>为什么是端口</b>：wiki 的批次处理、引用管线、图片富化与清理都要读/删 knowledge 的
 * chunk。此前 wiki 直接注入 knowledge 域的具体适配器，构成 {@code wiki → knowledge} 的
 * <b>实现类直连</b>（与 {@code knowledge → wiki.service} 一起形成域级循环）。抽出该端口后，
 * wiki 只依赖契约，实现在 knowledge 域内。</p>
 *
 * <p>{@code tenantId} 由调用方显式传入：wiki 的异步批次跑在线程池里，不依赖请求上下文。</p>
 */
public interface ChunkPort {

    /** 按知识文档读取文本 chunk（按 chunkIndex 升序）。 */
    List<ChunkView> textChunks(long tenantId, String knowledgeId);

    /** 按 id 批量读取 chunk；不存在的 id 直接跳过。 */
    List<ChunkView> chunksByIds(long tenantId, List<String> ids);

    /** 删除单个 chunk（重摄取幂等）。 */
    void deleteChunk(long tenantId, String chunkId);

    /** 把 wiki 图片标记解析成可渲染引用，替换正文里的占位。 */
    String enrichContentWithImageInfo(String content, long tenantId, List<String> textChunkIds);
}
