package com.ragagent.knowledge.api;

import com.ragagent.contract.knowledge.KnowledgeIngest;
import com.ragagent.knowledge.support.KnowledgeHashes;
import com.ragagent.knowledge.support.KnowledgePaths;
import com.ragagent.knowledge.support.KnowledgeTaskIdCodec;
import com.ragagent.knowledge.task.KnowledgeProcessingQueue;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeIngest} 的知识域实现：逐字转发域内四件（哈希 / 路径 / 任务号 / 队列），
 * 不新增缓冲或改写（异常语义同域内）。
 */
@Component
public class KnowledgeIngestAdapter implements KnowledgeIngest {

    private final KnowledgeProcessingQueue processingQueue;

    public KnowledgeIngestAdapter(KnowledgeProcessingQueue processingQueue) {
        this.processingQueue = processingQueue;
    }

    @Override
    public String contentHash(byte[] content) {
        return KnowledgeHashes.md5Hex(content);
    }

    @Override
    public String fileNameFromUrl(String url) {
        return KnowledgePaths.extractFileNameFromUrl(url);
    }

    @Override
    public String newTaskId(String taskType, long tenantId, String businessId) {
        return KnowledgeTaskIdCodec.generateTaskId(taskType, tenantId, businessId);
    }

    @Override
    public void enqueueProcessing(String knowledgeId) {
        processingQueue.enqueue(knowledgeId);
    }
}
