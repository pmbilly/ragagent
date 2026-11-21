package com.ragagent.knowledge.wiki.service.ingest;

import com.ragagent.framework.concurrent.SingleFlight;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * wiki 提示词预热的运行态状态：单飞锁、预热标记表、标记回收器。
 *
 * <p><b>为什么单独成类</b>：这三样是<b>运行态状态</b>而非注入依赖，原先直接挂在
 * {@code WikiIngestService} 字段上，由 {@code WikiIngestLlmSupport} 通过回引访问。
 * 抽出来后 ingest 主服务与 llm 协作者<b>共享同一实例</b>——两侧必须拿到同一个对象，
 * 否则单飞锁与预热标记各算各的，并发合并与预热串行都会静默失效。</p>
 *
 * <p>生命周期：随 {@code WikiIngestService} 构造而创建，进程内单例；回收线程为 daemon，
 * 不阻塞关停。</p>
 */
final class LlmWarmupState {

    /** 合并进程内<b>字节完全相同</b>的并发 prompt。 */
    final SingleFlight llmRequests = new SingleFlight();

    /**
     * 只串行化同一个可复用 Wiki 页面前缀的<b>首个</b>请求；
     * 其它前缀与已经预热过的同类保持并行。
     */
    final ConcurrentHashMap<String, PromptWarmup> promptWarmups = new ConcurrentHashMap<>();

    /** 预热标记的回收器：预热完成后延迟数分钟移除标记（覆盖并行 reduce 突发，又不常驻缓存）。 */
    final ScheduledExecutorService warmupReaper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "wiki-prompt-warmup-reaper");
        t.setDaemon(true);
        return t;
    });

    /** 一次预热的完成标记（done 完成信号 + closed 幂等关闭位） */
    static final class PromptWarmup {
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final AtomicBoolean closed = new AtomicBoolean(false);
    }

    /** 供可观测/测试：当前在飞行的 LLM 请求数 */
    int inflightCount() {
        return llmRequests.inflightCount();
    }
}
