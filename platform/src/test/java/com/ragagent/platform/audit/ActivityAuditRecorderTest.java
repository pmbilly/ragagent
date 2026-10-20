package com.ragagent.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.audit.AuditOutcome;
import com.ragagent.contract.audit.AuditWritePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityAuditRecorderTest {

    private final List<AuditWritePort.AuditRecord> written = new ArrayList<>();

    private final ActivityAuditRecorder recorder = new ActivityAuditRecorder(written::add);

    @Test
    void writesScopeActorAndAlphabeticalDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceType", "faq");
        details.put("entryId", 7L);

        recorder.record(13L, "knowledge_base", "kb-1", "knowledge.updated",
                "faq_entry", "chunk-1", details);

        assertThat(written).hasSize(1);
        AuditWritePort.AuditRecord record = written.get(0);
        assertThat(record.tenantId()).isEqualTo(13L);
        assertThat(record.scopeType()).isEqualTo("knowledge_base");
        assertThat(record.scopeId()).isEqualTo("kb-1");
        assertThat(record.action()).isEqualTo("knowledge.updated");
        assertThat(record.targetType()).isEqualTo("faq_entry");
        assertThat(record.targetId()).isEqualTo("chunk-1");
        assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.detailsJson()).isEqualTo("{\"entryId\":7,\"sourceType\":\"faq\"}");
    }

    @Test
    void taskContextAddsTaskFieldsAndProcessingStatus() {
        recorder.record(13L, "knowledge_base", "kb-1", "datasource.synced",
                "data_source", "ds-1", AuditOutcome.FAILED, Map.of("count", 2),
                "task-1", "manual", true, false);

        assertThat(written).hasSize(1);
        assertThat(written.get(0).detailsJson())
                .isEqualTo("{\"count\":2,\"processingStatus\":\"failed\",\"taskId\":\"task-1\",\"trigger\":\"manual\"}");
    }

    @Test
    void suppressedAndMissingScopeAreDropped() {
        recorder.record(13L, "knowledge_base", "kb-1", "knowledge.updated",
                "faq_entry", "chunk-1", AuditOutcome.SUCCESS, Map.of(), "", "", true, true);
        recorder.record(13L, "knowledge_base", "", "knowledge.updated",
                "faq_entry", "chunk-1", Map.of());

        assertThat(written).isEmpty();
    }

    @Test
    void tenantFallsBackToContextAndZeroIsDropped() {
        recorder.record(0L, "knowledge_base", "kb-1", "knowledge.updated",
                "faq_entry", "chunk-1", Map.of());

        assertThat(written).isEmpty();
    }
}
