package com.ragagent.contract.knowledge;

import java.util.List;

/**
 * 文档**分块**契约端口：把纯文本按策略切分为带序号/偏移的分块。
 *
 * <p><b>为什么有它</b>：分块实现（{@code knowledge/chunker}）已归位知识域（方案 落点），
 * 而会话的临时文档处理（{@code TemporaryDocumentProcessor}）需要同一能力 ⇒ 按跨域只经契约的规则，
 * 把能力提到本端口（HANDOFF 判据 2：跨域能力/配置/数据读取）。</p>
 *
 * <p><b>语义</b>：与域内 {@code Chunker.split} 逐字同一实现（本端口只是能力面收窄，不改切分行为）；
 * 配置面只暴露跨域实际设置的三个字段（chunkSize/chunkOverlap/strategy），其余取域内默认值。</p>
 */
public interface DocumentChunkingPort {

    /** 自动策略（等价域内 {@code Chunker.STRATEGY_AUTO}）。 */
    String STRATEGY_AUTO = "auto";

    /** 分块配置视图（跨域面：只含调用方实际设置的字段）。 */
    record SplitterConfigView(int chunkSize, int chunkOverlap, String strategy) {
    }

    /** 单个分块（跨域面字段；{@code embeddingContent} 为"内容 + 上下文头"的嵌入用文本）。 */
    record ChunkedPart(String content, String contextHeader, String embeddingContent,
                       int seq, int start, int end) {
    }

    /** 按配置切分（纯函数）。 */
    List<ChunkedPart> split(String content, SplitterConfigView config);
}
