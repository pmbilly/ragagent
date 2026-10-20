package com.ragagent.knowledge.domain.ingest;

/**
 * 一次入库尝试的内存态：当前阶段 + 状态。
 *
 * <p><b>不是持久化模型</b>：完成状态的权威来源仍是 {@code knowledge.parse_status}
 * 与 span 根行；本对象只用于处理链路内传递阶段与终态，避免在 worker 里散落局部变量。</p>
 */
public record IngestJob(String knowledgeId, int attempt, IngestStage stage, Status status) {

    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED }

    public static IngestJob start(String knowledgeId, int attempt) {
        return new IngestJob(knowledgeId, attempt, null, Status.PENDING);
    }

    /** 进入下一个阶段；终态后不可再推进。 */
    public IngestJob progress(IngestStage next) {
        requireActive();
        return new IngestJob(knowledgeId, attempt, next, Status.PROCESSING);
    }

    public IngestJob completed() {
        requireActive();
        return new IngestJob(knowledgeId, attempt, stage, Status.COMPLETED);
    }

    public IngestJob failed() {
        requireActive();
        return new IngestJob(knowledgeId, attempt, stage, Status.FAILED);
    }

    private void requireActive() {
        if (status == Status.COMPLETED || status == Status.FAILED) {
            throw new IllegalStateException(
                    "ingest job already terminal: " + status + " (knowledge=" + knowledgeId + ")");
        }
    }
}
