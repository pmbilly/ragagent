package com.ragagent.wiki.domain.ingest;

/**
 * 任务重试耗尽后的槽位收尾端口。
 *
 * <p>队列实现只在死信归档时通过它释放该知识库被遗弃的 finalizing 槽位。
 * 收窄到这一个方法，队列不必持有整个 ingest 用例。</p>
 */
public interface WikiIngestSlotReleaser {

    /** 释放该知识库在途 op 对应文档的 finalizing 槽位。 */
    void releaseSlotsForAbandonedTask(String kbId);
}
