package com.ragagent.knowledge.domain.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ragagent.knowledge.domain.KnowledgeProcessingSpan;
import org.junit.jupiter.api.Test;

class IngestJobTest {

    @Test
    void progressesThroughStages() {
        IngestJob job = IngestJob.start("k-1", 1);
        assertThat(job.status()).isEqualTo(IngestJob.Status.PENDING);
        assertThat(job.stage()).isNull();

        job = job.progress(IngestStage.DOC_READER);
        assertThat(job.stage()).isEqualTo(IngestStage.DOC_READER);
        assertThat(job.status()).isEqualTo(IngestJob.Status.PROCESSING);

        job = job.progress(IngestStage.CHUNKING)
                .progress(IngestStage.EMBEDDING)
                .progress(IngestStage.POST_PROCESS)
                .completed();
        assertThat(job.status()).isEqualTo(IngestJob.Status.COMPLETED);
        assertThat(job.stage()).isEqualTo(IngestStage.POST_PROCESS);
    }

    @Test
    void terminalStateCannotBeAdvanced() {
        IngestJob failed = IngestJob.start("k-2", 1)
                .progress(IngestStage.DOC_READER)
                .failed();
        assertThat(failed.status()).isEqualTo(IngestJob.Status.FAILED);
        assertThatThrownBy(() -> failed.progress(IngestStage.CHUNKING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::completed)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stageCodesMatchSpanConstants() {
        assertThat(IngestStage.DOC_READER.code()).isEqualTo(KnowledgeProcessingSpan.STAGE_DOC_READER);
        assertThat(IngestStage.CHUNKING.code()).isEqualTo(KnowledgeProcessingSpan.STAGE_CHUNKING);
        assertThat(IngestStage.EMBEDDING.code()).isEqualTo(KnowledgeProcessingSpan.STAGE_EMBEDDING);
        assertThat(IngestStage.MULTIMODAL.code()).isEqualTo(KnowledgeProcessingSpan.STAGE_MULTIMODAL);
        assertThat(IngestStage.POST_PROCESS.code()).isEqualTo(KnowledgeProcessingSpan.STAGE_POST_PROCESS);
        assertThat(KnowledgeProcessingSpan.ALL_STAGES).containsExactlyElementsOf(IngestStage.codes());
    }
}
