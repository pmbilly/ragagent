package com.ragagent.knowledge.task;

import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.retrieval.RetrieveGraphRepository;
import com.ragagent.contract.retrieval.VectorIndexStore;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.knowledge.config.KnowledgeOcrProperties;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.domain.ingest.IngestJob;
import com.ragagent.knowledge.domain.ingest.IngestStage;
import com.ragagent.knowledge.domain.span.SpanTracker;
import com.ragagent.knowledge.index.ChunkVectorIndexer;
import com.ragagent.knowledge.index.KnowledgeVectorWrites;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.repository.IngestStateRepository;
import com.ragagent.knowledge.repository.KnowledgeBaseRepository;
import com.ragagent.knowledge.repository.KnowledgeRepository;
import com.ragagent.knowledge.service.image.ExtractedImageStore;
import com.ragagent.knowledge.storage.TenantFileStorage;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.access.TenantScopes;
import com.ragagent.platform.access.TracingContext;
import com.ragagent.platform.tracing.langfuse.LangfuseTaskScope;
import com.ragagent.platform.tracing.langfuse.LangfuseTracing;
import com.ragagent.wiki.service.DefaultWikiKnowledgeFinalizer;
import com.ragagent.wiki.service.ingest.WikiIngestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 知识处理后台 worker：虚拟线程队列消费 knowledge 的解析主链路
 * （接管 → 预清理 → 取文本 → 分块落库 → 向量化 → 富化 fan-out / 完成），对外仅暴露
 * {@link #enqueue(String)}。跨线程显式传值（TenantContext 不共享，各阶段自行解析租户）。
 * <p><b>队列语义（名字易误解）</b>：本类实现 {@code KnowledgeProcessingQueue}，但投递走的是
 * <b>进程内虚拟线程执行器</b>（{@code newVirtualThreadPerTaskExecutor}）——<b>没有跨实例队列</b>。
 * 多副本部署时，上传请求落到哪个副本就由哪个副本处理；进程重启会丢掉在飞任务
 * （由启动恢复任务扫 pending 补投）。跨实例队列只覆盖<b>富化子任务</b>
 * （{@code RedisQuestionGenerationTaskQueue} 等，见 {@code KnowledgeRedisWiring}）。</p>
 * <p>状态机：pending →(CAS)→ processing →（无富化子任务）→ completed +
 * enable_status=enabled + processed_at；任一步失败 → failed + error_message；
 * deleting/cancelled 检查点短路。</p>
 * <p><b>事务边界</b>：整条链路<b>不包一个大事务</b>——每阶段独立提交，靠上述状态机 +
 * 幂等重入保证最终一致（解析/向量化是外部调用，包在事务里会长时间持锁）。需要原子性的
 * 多步写（如 chunk 行 + 向量索引元数据）各自经 {@code platform.persistence.TxTemplate}。</p>
 * <p>三个关键不变量：</p>
 * <ol>
 * <li><b>索引文本形态</b>：嵌入文本与 embeddings.content 一致，均为
 * title 前缀 + ContextHeader + trim 正文（BM25 与向量评分对象必须同源）；</li>
 * <li><b>重处理预清理</b>：先删旧 chunks 行（无条件）+ 全部向量行（仅向量化启用
 * 且模型可用时），失败清理只清本次产物；模型解析失败发生在预清理之前——
 * 既有数据保持不动；</li>
 * <li><b>wiki 交接</b>：wiki_enabled 且有文本 chunk 时 processing 原子晋升
 * finalizing（pending_subtasks_count 由子任务持有）再入队 ingest，
 * 由 DefaultWikiKnowledgeFinalizer 递减并晋升 completed。</li>
 * </ol>
 */
@Service
public class KnowledgeProcessWorker implements KnowledgeProcessingQueue {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeProcessWorker.class);

    /** 任务观测的 span/根名。 */
    static final String TASK_TYPE_DOCUMENT_PROCESS = "document:process";

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private final KnowledgeRepository knowledgeRepository;
    private final IngestStateRepository ingestStateRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeTextExtractor textExtractor;
    private final KnowledgeChunkWriter chunkWriter;
    private final KnowledgeIndexStage indexStage;
    private final KnowledgeFinalizingStage finalizingStage;
    private final SpanTracker spanTracker;

    public KnowledgeProcessWorker(KnowledgeRepository knowledgeRepository,
                                  IngestStateRepository ingestStateRepository,
                                  KnowledgeBaseRepository kbRepository,
                                  ChunkRepository chunkRepository,
                                  TenantFileStorage fileStorage,
                                  ExtractedImageStore extractedImageStore,
                                  DocReaderClient docReader,
                                  KnowledgeOcrProperties ocrProperties,
                                  VectorIndexStore vectorStore,
                                  ModelCatalogPort modelCatalog,
                                  KnowledgeVectorWrites vectorWrites,
                                  ChunkVectorIndexer chunkVectorIndexer,
                                  ModelRuntime modelRuntimeAssembler,
                                  ApplicationEventPublisher eventPublisher,
                                  SpanTracker spanTracker,
                                  RetrieveGraphRepository graphRepository,
                                  ObjectProvider<
                                          WikiIngestService> wikiIngestService,
                                  ObjectProvider<
                                          DefaultWikiKnowledgeFinalizer> wikiKnowledgeFinalizer,
                                  ObjectProvider<
                                          ChunkExtractTaskQueue> chunkExtractQueue,
                                  ObjectProvider<
                                          QuestionGenerationTaskQueue> questionGenerationQueue) {
        this.knowledgeRepository = knowledgeRepository;
        this.ingestStateRepository = ingestStateRepository;
        this.kbRepository = kbRepository;
        this.spanTracker = spanTracker;
        this.textExtractor = new KnowledgeTextExtractor(
                fileStorage, extractedImageStore, docReader, kbRepository,
                new KnowledgeImageOcr(fileStorage, ocrProperties, spanTracker), spanTracker);
        this.chunkWriter = new KnowledgeChunkWriter(chunkRepository, spanTracker);
        this.indexStage = new KnowledgeIndexStage(
                chunkRepository, extractedImageStore, vectorStore, modelCatalog, vectorWrites,
                chunkVectorIndexer, modelRuntimeAssembler, graphRepository, spanTracker);
        this.finalizingStage = new KnowledgeFinalizingStage(
                knowledgeRepository, ingestStateRepository, kbRepository, chunkRepository,
                eventPublisher, spanTracker,
                wikiIngestService, wikiKnowledgeFinalizer, chunkExtractQueue, questionGenerationQueue);
    }

    @Override
    public void enqueue(String knowledgeId) {
        // 入队侧注入：在提交线程
        // （HTTP 请求线程）capture 当前 traceparent，随任务带到 worker 线程续接同一棵树。
        TracingContext tracing =
                LangfuseTracing.inject();
        // 租户同理在提交线程 capture（worker 线程查不到自建模型 ⇒ 1003，见 KnowledgeTenantResolver）。
        Long tenantId = TenantContext.currentTenantId();
        executor.submit(() -> process(knowledgeId, tracing, tenantId));
    }

    /** 任务入口：接 trace + 包 document:process span；并进入知识行租户作用域（见
     * {@link KnowledgeTenantResolver}）。包级可见以供同包回归测试驱动。 */
    void process(String knowledgeId,
                 TracingContext tracing,
                 Long capturedTenantId) {
        LangfuseTaskScope scope =
                LangfuseTaskScope.start(
                        TASK_TYPE_DOCUMENT_PROCESS, tracing,
                        Map.of("knowledge_id", knowledgeId),
                        LangfuseTaskScope.previewPayload(knowledgeId));
        try {
            try (TenantScopes.TenantScope ignored =
                         TenantScopes.enterCaptured(KnowledgeTenantResolver.resolve(
                         knowledgeRepository, knowledgeId, capturedTenantId))) {
                processInner(knowledgeId, scope);
            }
        } finally {
            scope.close();
        }
    }

    private void processInner(String knowledgeId,
                              LangfuseTaskScope scope) {
        // CAS pending → processing：被抢/已取消则静默退出
        int updated = ingestStateRepository.casPendingToProcessing(knowledgeId,
                OffsetDateTime.now(ZoneOffset.UTC));
        if (updated == 0) {
            return;
        }
        // 分配本 attempt 的 span 树（best-effort——追踪器绝不阻断处理）
        int attempt = 0;
        try {
            attempt = spanTracker.openAttempt(knowledgeId,
                    LangfuseTracing.currentTraceId()).attempt();
        } catch (RuntimeException e) {
            log.warn("[SpanTracker] openAttempt failed kid={}", knowledgeId, e);
        }
        IngestJob job = IngestJob.start(knowledgeId, attempt);
        try {
            Knowledge k = knowledgeRepository.findByIdAny(knowledgeId);
            if (k == null || k.isAborted()) {
                return;
            }
            KnowledgeBase kb = kbRepository.findByIdAny(k.getKnowledgeBaseId());
            if (kb == null) {
                throw new IllegalStateException("knowledge base not found");
            }
            String embeddingModelId = indexStage.resolveEmbeddingModelId(k, kb);
            indexStage.preClean(k, kb, embeddingModelId, knowledgeId);
            //：记下本次尝试的 chunk id——失败清场只删它们（见 cleanupFailedAttempt）。
            List<String> attemptChunkIds = List.of();
            try {
                job = job.progress(IngestStage.DOC_READER);
                String markdown = textExtractor.extract(k, attempt, knowledgeId);
                if (knowledgeRepository.findByIdAny(knowledgeId).isAborted()) {
                    return; // 中途取消检查点
                }
                job = job.progress(IngestStage.CHUNKING);
                List<Chunk> chunks = chunkWriter.chunkAndPersist(
                        k, kb, knowledgeId, markdown, attempt);
                attemptChunkIds = chunks.stream().map(Chunk::getId).toList();
                job = job.progress(IngestStage.EMBEDDING);
                indexStage.embedChunks(k, kb, embeddingModelId, chunks, knowledgeId, attempt);
                job = job.progress(IngestStage.POST_PROCESS);
                finalizingStage.finalizeProcessing(k, kb, knowledgeId, attempt, chunks);
                job = job.completed();
                log.debug("process knowledge {} completed at {}", knowledgeId, job.stage());
            } catch (Exception inner) {
                indexStage.cleanupFailedAttempt(knowledgeId, attemptChunkIds, k, kb, embeddingModelId);
                throw inner;
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            // 处理体抛错 → span/根记 outcome=error（scope.finish 幂等，随后的 close 不会覆盖）
            scope.finish("error", message);
            job = job.failed();
            log.warn("process knowledge {} failed stage={} status={}",
                    knowledgeId, job.stage(), job.status(), e);
            finalizingStage.failOrComplete(knowledgeId, attempt, message);
        }
    }

}
