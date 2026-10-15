package com.ragagent.common.knowledge.support;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.ragagent.common.knowledge.KnowledgeDocumentFacts;
import com.ragagent.common.knowledge.domain.Knowledge;

/**
 * knowledge 实体 → L1/线格式载荷的**域内公用投影**（由 {@code service.KnowledgeService}
 * 的静态助手抽出；与 的 {@link ChunkProjectionAdapter} 同一条思路——"实体 → 载荷"的
 * 映射只留一份，跨面复用不漂移）。
 *
 * <p>含：{@code factsOf(List<Knowledge>)} / {@code factsOf(Knowledge)}（→ {@link KnowledgeDocumentFacts}）、
 * {@code timeString(OffsetDateTime)}（线格式时间）。
 * 依赖方向：support ← 任意面（本类不依赖任何 service）。</p>
 */
public final class KnowledgeProjectionAdapter {

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
    /** 同包开放（KnowledgeSpanService 渲染 span 时间戳复用，不各自复制）。 */
    public static String timeString(OffsetDateTime v) {
        return v.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
