package com.ragagent.common.knowledge.support;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.platform.web.JsonMappers;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ragagent.common.knowledge.KnowledgeDocumentFacts;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeTag;

/**
 * knowledge 实体 → L1/线格式载荷的**域内公用投影**（由 {@code service.KnowledgeService}
 * 的静态助手抽出；与 的 {@link ChunkProjectionAdapter} 同一条思路——"实体 → 载荷"的
 * 映射只留一份，跨面复用不漂移）。
 *
 * <p>含：{@code factsOf(List<Knowledge>)} / {@code factsOf(Knowledge)}（→ {@link KnowledgeDocumentFacts}）、
 * {@code tagView(KnowledgeTag)}（→ 标签 JSON 视图）、{@code timeString(OffsetDateTime)}（线格式时间）。
 * 依赖方向：support ← 任意面（本类不依赖任何 service）。</p>
 */
public final class KnowledgeProjectionAdapter {

    /** 与全局 Jackson 配置同式（随 {@code tagView} 一起从 KnowledgeService 抽出）。 */
    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private KnowledgeProjectionAdapter() {
    }

    /** 批量投影（保持入参顺序）。 */
    public static List<KnowledgeDocumentFacts> factsOf(List<Knowledge> rows) {
        List<KnowledgeDocumentFacts> out = new ArrayList<>();
        for (Knowledge k : rows) {
            out.add(factsOf(k));
        }
        return out;
    }
    /**
     * 实体 → L1 facts 投影（域内公用：端口实现与装配层共用一份映射，避免漂移）。
     * 仅带调用方真正读取的字段。
     */


    public static KnowledgeDocumentFacts factsOf(Knowledge k) {
        if (k == null) {
            return null;
        }
        return new KnowledgeDocumentFacts(k.getId(), k.getTitle(), k.getMetadata(),
                k.getFileName(), k.getSource(), k.getChannel(), k.getDescription(),
                k.getKnowledgeBaseId(), k.getTenantId(), k.getFileType(), k.getFilePath());
    }
    /** 标签视图（时间序列化与全局 Jackson 配置同式）。 */
    public static ObjectNode tagView(KnowledgeTag t) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", t.getId());
        n.put("seqId", t.getSeqId() == null ? 0L : t.getSeqId());
        n.put("knowledgeBaseId", t.getKnowledgeBaseId());
        n.put("name", t.getName());
        n.put("color", t.getColor() == null ? "" : t.getColor());
        n.put("sortOrder", t.getSortOrder() == null ? 0 : t.getSortOrder());
        n.put("createdAt", t.getCreatedAt() == null ? null : timeString(t.getCreatedAt()));
        n.put("updatedAt", t.getUpdatedAt() == null ? null : timeString(t.getUpdatedAt()));
        return n;
    }
    /** 同包开放（KnowledgeSpanService 渲染 span 时间戳复用，不各自复制）。 */
    public static String timeString(OffsetDateTime v) {
        return v.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
