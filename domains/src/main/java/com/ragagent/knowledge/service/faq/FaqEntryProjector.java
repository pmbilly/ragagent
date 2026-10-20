package com.ragagent.knowledge.service.faq;

import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.domain.KnowledgeTag;
import com.ragagent.knowledge.dto.faq.FaqEntry;
import com.ragagent.knowledge.repository.KnowledgeTagRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ chunk → 响应条目的唯一投影：tag 只查一次，seqId 映射与 tagName 回填同一处。
 *
 * <p>创建/更新/相似问/批量字段四条尾路径此前各抄一遍，且每条都把同一个 tag 查两次
 * （一次取 seqId、一次取 name）；收敛后规则变更只改这里。</p>
 */
final class FaqEntryProjector {

    private final KnowledgeTagRepository tagRepository;
    private final FaqChunkCodec codec;

    FaqEntryProjector(KnowledgeTagRepository tagRepository, FaqChunkCodec codec) {
        this.tagRepository = tagRepository;
        this.codec = codec;
    }

    FaqEntry toEntry(long tenantId, Chunk chunk, KnowledgeBase kb) {
        Map<String, Long> tagSeqIdMap = new LinkedHashMap<>();
        KnowledgeTag tag = null;
        if (!chunk.getTagId().isEmpty()) {
            tag = tagRepository.listTagsByIds(tenantId, List.of(chunk.getTagId()))
                    .stream().findFirst().orElse(null);
            if (tag != null) {
                tagSeqIdMap.put(tag.getId(), tag.getSeqId());
            }
        }
        FaqEntry entry = codec.chunkToFAQEntry(chunk, kb, tagSeqIdMap);
        return tag == null ? entry : entry.withTagName(tag.getName());
    }
}
