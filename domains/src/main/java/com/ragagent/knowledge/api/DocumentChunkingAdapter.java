package com.ragagent.knowledge.api;

import java.util.ArrayList;
import java.util.List;

import com.ragagent.contract.knowledge.DocumentChunkingPort;
import com.ragagent.knowledge.chunker.Chunker;
import com.ragagent.knowledge.chunker.ParsedChunk;
import com.ragagent.knowledge.chunker.SplitterConfig;
import org.springframework.stereotype.Component;

/**
 * {@link DocumentChunkingPort} 的知识域实现：逐字转发域内 {@code Chunker}（不改切分语义）。
 */
@Component
public class DocumentChunkingAdapter implements DocumentChunkingPort {

    @Override
    public List<ChunkedPart> split(String content, SplitterConfigView config) {
        SplitterConfig cfg = new SplitterConfig();
        cfg.setChunkSize(config.chunkSize());
        cfg.setChunkOverlap(config.chunkOverlap());
        cfg.setStrategy(config.strategy());
        List<ParsedChunk> parts = Chunker.split(content, cfg);
        List<ChunkedPart> out = new ArrayList<>(parts.size());
        for (ParsedChunk p : parts) {
            out.add(new ChunkedPart(p.getContent(), p.getContextHeader(), p.embeddingContent(),
                    p.getSeq(), p.getStart(), p.getEnd()));
        }
        return out;
    }
}
