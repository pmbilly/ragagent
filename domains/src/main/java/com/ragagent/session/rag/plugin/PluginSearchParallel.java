package com.ragagent.session.rag.plugin;

import com.ragagent.contract.retrieval.SearchResult;
import com.ragagent.session.rag.ChatManage;
import com.ragagent.session.rag.PipelineCommon;
import com.ragagent.session.rag.PipelineEventType;
import com.ragagent.session.rag.PipelineLog;
import com.ragagent.session.rag.support.SearchSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CHUNK_SEARCH_PARALLEL 阶段插件：
 * 并发跑 chunk 检索（复用 {@link PluginSearch}）与实体检索（复用
 * {@link PluginSearchEntity}），两路结果合并去重。
 *
 * <p>两个内部插件由装配层显式传入、不注册到 manager——依赖写在构造器上，
 * 而不是藏在类体里。两者都是无状态服务引用，可与 CHUNK_SEARCH / ENTITY_SEARCH
 * 的注册实例共用。</p>
 *
 * <p>Deep-copy：并发任务各自持有 {@code cloneChatManage()} 的副本（检索结果清空）
 * 避免并发读写共享列表。SEARCH_NOTHING 会被吞掉（两个检索任务把它归为无结果）；
 * 合并序恒 chunk 在前 entity 在后（确定性）。全空时优先返回 chunk_search 的错误
 * （errs map 命中），否则 SEARCH_NOTHING。</p>
 */
public final class PluginSearchParallel implements Plugin {

    private final PluginSearch searchPlugin;
    private final PluginSearchEntity searchEntityPlugin;

    public PluginSearchParallel(PluginSearch searchPlugin, PluginSearchEntity searchEntityPlugin) {
        this.searchPlugin = searchPlugin;
        this.searchEntityPlugin = searchEntityPlugin;
    }

    @Override
    public PipelineEventType[] activationEvents() {
        return new PipelineEventType[] {PipelineEventType.CHUNK_SEARCH_PARALLEL};
    }

    @Override
    public PluginError onEvent(PipelineEventType eventType, ChatManage chatManage, Plugin.Chain next) {
        if (!chatManage.needsRetrieval()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("sessionId", chatManage.getSessionId());
            f.put("reason", "intent_no_search");
            PipelineLog.info("SearchParallel", "skip", f);
            return next.next();
        }

        Map<String, Object> start = new LinkedHashMap<>();
        start.put("sessionId", chatManage.getSessionId());
        start.put("has_entities", chatManage.getEntityContext().getEntity() != null && !chatManage.getEntityContext().getEntity().isEmpty());
        start.put("rewrite_query", chatManage.getQueryPlan().getRewriteQuery());
        PipelineLog.info("SearchParallel", "start", start);

        ChatManage chunkCM = chatManage.cloneChatManage();
        chunkCM.getSearchOutcome().setResults(null);
        ChatManage entityCM = chatManage.cloneChatManage();
        entityCM.getSearchOutcome().setResults(null);

        final ChatManage fChunkCM = chunkCM;
        final ChatManage fEntityCM = entityCM;
        final ChatManage fOrig = chatManage;

        Map<String, PluginError> errs = PipelineCommon.runParallel(
                new PipelineCommon.ParallelTask("chunk_search", () -> {
                    PluginError err = searchPlugin.onEvent(
                            PipelineEventType.CHUNK_SEARCH, fChunkCM, () -> null);
                    boolean isNothing = err == PluginError.SEARCH_NOTHING;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("resultCount", fChunkCM.getSearchOutcome().getResults() == null ? 0 : fChunkCM.getSearchOutcome().getResults().size());
                    f.put("has_error", err != null && !isNothing);
                    PipelineLog.info("SearchParallel", "chunk_search_done", f);
                    return isNothing ? null : err;
                }),
                new PipelineCommon.ParallelTask("entity_search", () -> {
                    if (fOrig.getEntityContext().getEntity() == null || fOrig.getEntityContext().getEntity().isEmpty()) {
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("reason", "no_entities");
                        PipelineLog.info("SearchParallel", "entity_search_skip", f);
                        return null;
                    }
                    PluginError err = searchEntityPlugin.onEvent(
                            PipelineEventType.ENTITY_SEARCH, fEntityCM, () -> null);
                    boolean isNothing = err == PluginError.SEARCH_NOTHING;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("resultCount", fEntityCM.getSearchOutcome().getResults() == null ? 0 : fEntityCM.getSearchOutcome().getResults().size());
                    f.put("has_error", err != null && !isNothing);
                    PipelineLog.info("SearchParallel", "entity_search_done", f);
                    return isNothing ? null : err;
                }));

        // 合并两路结果（chunk 在前 entity 在后）+ 去重
        List<SearchResult> merged = new ArrayList<>();
        if (chunkCM.getSearchOutcome().getResults() != null) {
            merged.addAll(chunkCM.getSearchOutcome().getResults());
        }
        if (entityCM.getSearchOutcome().getResults() != null) {
            merged.addAll(entityCM.getSearchOutcome().getResults());
        }
        chatManage.getSearchOutcome().setResults(SearchSupport.removeDuplicateResults(merged));

        for (Map.Entry<String, PluginError> e : errs.entrySet()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("task", e.getKey());
            f.put("err", e.getValue() == null ? null
                    : (e.getValue().cause() == null ? e.getValue().errorType : e.getValue().cause().getMessage()));
            PipelineLog.warn("SearchParallel", "task_error", f);
        }

        Map<String, Object> done = new LinkedHashMap<>();
        done.put("sessionId", chatManage.getSessionId());
        done.put("chunk_results", chunkCM.getSearchOutcome().getResults() == null ? 0 : chunkCM.getSearchOutcome().getResults().size());
        done.put("entity_results", entityCM.getSearchOutcome().getResults() == null ? 0 : entityCM.getSearchOutcome().getResults().size());
        done.put("total_results", chatManage.getSearchOutcome().getResults() == null ? 0 : chatManage.getSearchOutcome().getResults().size());
        done.put("error_count", errs.size());
        PipelineLog.info("SearchParallel", "complete", done);

        if (chatManage.getSearchOutcome().getResults() == null || chatManage.getSearchOutcome().getResults().isEmpty()) {
            PluginError err = errs.get("chunk_search");
            if (err != null) {
                return err;
            }
            return PluginError.SEARCH_NOTHING;
        }

        return next.next();
    }
}
