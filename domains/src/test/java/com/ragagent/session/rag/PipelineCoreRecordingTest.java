package com.ragagent.session.rag;

import static com.ragagent.session.rag.Rec46cSupport.assertRec;
import static com.ragagent.session.rag.Rec46cSupport.errOf;
import static com.ragagent.session.rag.Rec46cSupport.json;

import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.contract.session.PipelineMessageAttachmentView;
import com.ragagent.session.dto.PipelineUsedMemoryView;
import com.ragagent.session.rag.plugin.PluginError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 录制回放：event_manager / builder / plugin_error / chat_manage 组
 * （期望值 = {@link ChatPipelineGolden} 的录制常量）。
 */
class PipelineCoreRecordingTest {

    // ----- event_manager -----

    @Test
    void eventManager() {
        List<String> calls = new ArrayList<>();
        EventManager mgr = new EventManager();
        var p1 = new Rec46cSupport.TestPlugin("p1",
                new PipelineEventType[] {PipelineEventType.CHUNK_SEARCH}, null, calls);
        var p2 = new Rec46cSupport.TestPlugin("p2",
                new PipelineEventType[] {PipelineEventType.CHUNK_SEARCH}, null, calls);
        var p3 = new Rec46cSupport.TestPlugin("p3",
                new PipelineEventType[] {PipelineEventType.CHUNK_SEARCH},
                new PluginError(null, "boom", "boom"), calls);
        mgr.register(p1);
        mgr.register(p2);
        mgr.register(p3);

        PluginError errNone = new EventManager().trigger(PipelineEventType.FILTER_TOP_K, new ChatManage());
        // 无错误时渲染为 "<nil>"（录制格式约定）
        assertRec("event_manager", "no_handler", errNone == null ? "<nil>" : json(errOf(errNone)));

        PluginError err = mgr.trigger(PipelineEventType.CHUNK_SEARCH, new ChatManage());
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("calls", calls);
        shape.put("err", errOf(err));
        assertRec("event_manager", "chain_with_error", json(shape));
    }

    // ----- builder（对照 recBuilder） -----

    @Test
    void builder() {
        List<PipelineEventType> p = PipelineBuilder.builder()
                .add(PipelineEventType.LOAD_HISTORY)
                .addIf(false, PipelineEventType.QUERY_UNDERSTAND)
                .addIf(true, PipelineEventType.CHAT_COMPLETION_STREAM)
                .build();
        assertRec("builder", "add_if", json(keys(p)));

        List<PipelineEventType> empty = PipelineBuilder.builder().build();
        assertRec("builder", "empty", json(keys(empty)));

        assertRec("builder", "rag_stream", json(keys(PipelineBuilder.presets().get("rag_stream"))));
        assertRec("builder", "rag", json(keys(PipelineBuilder.presets().get("rag"))));
        assertRec("builder", "chat_stream", json(keys(PipelineBuilder.presets().get("chat_stream"))));
    }

    private static List<String> keys(List<PipelineEventType> stages) {
        return stages.stream().map(PipelineEventType::key).toList();
    }

    @Test
    void pluginErrorWithError() {
        PluginError pp = PluginError.SEARCH_NOTHING.withError(new RuntimeException("no hits"));
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("base_type", PluginError.SEARCH_NOTHING.errorType);
        shape.put("clone", pp != PluginError.SEARCH_NOTHING);
        shape.put("err_type", pp.errorType);
        shape.put("desc", pp.description);
        shape.put("err", pp.cause().getMessage());
        assertRec("plugin_error", "with_error", json(shape));
    }

    // ----- chat_manage（对照 recChatManage） -----

    @Test
    void chatManageClone() {
        ChatManage cm = cloneFixture();
        ChatManage clone = cm.cloneChatManage();

        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("query", clone.getQuery());
        shape.put("sessionId", clone.getSessionId());
        shape.put("userId", clone.getUserId());
        shape.put("max_rounds", clone.getMaxRounds());
        shape.put("kb_ids", clone.getKnowledgeBaseIds());
        shape.put("knowledge_ids", clone.getKnowledgeIds());
        shape.put("targets", clone.getSearchTargets().size());
        shape.put("target0_tags", clone.getSearchTargets().get(0).tagIds());
        shape.put("target0_scope", clone.getSearchTargets().get(0).scopeTagIds());
        shape.put("target0_kids", clone.getSearchTargets().get(0).knowledgeIds());
        shape.put("target0_type", clone.getSearchTargets().get(0).type());
        shape.put("vector", clone.getVectorThreshold());
        shape.put("keyword", clone.getKeywordThreshold());
        shape.put("topk", clone.getEmbeddingTopK());
        shape.put("rerank_model", clone.getRerankModelId());
        shape.put("rerank_top_k", clone.getRerankTopK());
        shape.put("rerank_thresh", clone.getRerankThreshold());
        shape.put("chat_model", clone.getChatModelId());
        shape.put("citation", clone.getCitationEnabled());
        shape.put("rewrite", clone.isEnableRewrite());
        shape.put("expansion", clone.isEnableQueryExpansion());
        shape.put("faq_priority", clone.isFaqPriorityEnabled());
        shape.put("faq_thresh", clone.getFaqDirectAnswerThreshold());
        shape.put("faq_boost", clone.getFaqScoreBoost());
        shape.put("data_analysis", clone.isDataAnalysisEnabled());
        shape.put("images", clone.getImages());
        shape.put("vlm", clone.getVlmModelId());
        shape.put("vision", clone.isChatModelSupportsVision());
        shape.put("attachments", clone.getAttachments().size());
        shape.put("overrides", clone.getIntentPromptOverrides());
        shape.put("tenant", clone.getTenantId());
        shape.put("web_enabled", clone.isWebSearchEnabled());
        shape.put("web_provider", clone.getWebSearchProviderId());
        shape.put("web_max", clone.getWebSearchMaxResults());
        shape.put("fetch_enabled", clone.isWebFetchEnabled());
        shape.put("fetch_topn", clone.getWebFetchTopN());
        shape.put("language", clone.getLanguage());
        shape.put("rewrite_query", clone.getQueryPlan().getRewriteQuery());
        shape.put("intent", clone.getQueryPlan().getIntent());
        shape.put("history", 0);
        shape.put("image_desc", clone.getQueryPlan().getImageDescription());
        shape.put("quoted", clone.getQueryPlan().getQuotedContext());
        shape.put("sys_override", clone.getQueryPlan().getSystemPromptOverride());
        shape.put("memory_prompt", clone.getQueryPlan().getMemoryPrompt());
        shape.put("used_memories", clone.getUsedMemories().size());
        shape.put("entity", clone.getEntityContext().getEntity());
        shape.put("entity_kb", clone.getEntityContext().getEntityKbIds());
        shape.put("entity_knowledge", clone.getEntityContext().getEntityKnowledge());
        shape.put("rendered", clone.getAnswer().getRenderedContexts());
        shape.put("search_result", null);
        shape.put("rerank_result", null);
        shape.put("merge_result", null);
        shape.put("user_content", "");
        shape.put("chat_response", null);
        shape.put("event_bus", false);
        shape.put("messageId", "");
        shape.put("userMessageId", "");
        shape.put("summary_prompt", clone.getSummaryConfig().getPrompt());
        shape.put("summary_think", clone.getSummaryConfig().getThinking());
        assertRec("chat_manage", "clone", json(shape));

        // 就地改 clone 的 targets/kb 不影响原对象
        clone.getSearchTargets().set(0, new SearchTarget(
                clone.getSearchTargets().get(0).type(),
                clone.getSearchTargets().get(0).knowledgeBaseId(),
                clone.getSearchTargets().get(0).tenantId(),
                clone.getSearchTargets().get(0).knowledgeIds(),
                new ArrayList<>(clone.getSearchTargets().get(0).tagIds()) {{ add("t2"); }},
                clone.getSearchTargets().get(0).scopeTagIds(),
                clone.getSearchTargets().get(0).disableRecallThresholds()));
        clone.getKnowledgeBaseIds().set(0, "changed");
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("orig_kb0", cm.getKnowledgeBaseIds().get(0));
        deep.put("orig_tag_ids", cm.getSearchTargets().get(0).tagIds());
        assertRec("chat_manage", "clone_deep_copy", json(deep));
    }

    private ChatManage cloneFixture() {
        ChatManage cm = new ChatManage();
        cm.setQuery("q");
        cm.setSessionId("s1");
        cm.setUserId("u1");
        cm.setMaxRounds(3);
        cm.setKnowledgeBaseIds(new ArrayList<>(List.of("kb1")));
        cm.setKnowledgeIds(new ArrayList<>(List.of("k1")));
        cm.setSearchTargets(new ArrayList<>(java.util.Arrays.asList(
                new SearchTarget("knowledge_base", "kb1", 1,
                        List.of("kk"), List.of("t1"), List.of("st1"), false),
                null)));
        cm.setVectorThreshold(0.5);
        cm.setKeywordThreshold(0.7);
        cm.setEmbeddingTopK(4);
        cm.setVectorDatabase("pgvector");
        cm.setRerankModelId("rr");
        cm.setRerankTopK(3);
        cm.setRerankThreshold(0.2);
        cm.setChatModelId("cm");
        SummaryConfig sc = new SummaryConfig();
        sc.setPrompt("P");
        sc.setContextTemplate("CT");
        sc.setTemperature(0.7);
        sc.setTopP(0.9);
        sc.setSeed(1);
        sc.setMaxTokens(100);
        sc.setMaxCompletionTokens(200);
        sc.setFrequencyPenalty(0.1);
        sc.setPresencePenalty(0.2);
        sc.setThinking(true);
        cm.setSummaryConfig(sc);
        cm.setFallbackStrategy("fallback");
        cm.setFallbackResponse("fr");
        cm.setFallbackPrompt("fp");
        cm.setCitationEnabled(true);
        cm.setEnableRewrite(true);
        cm.setEnableQueryExpansion(true);
        cm.setRewritePromptSystem("rps");
        cm.setRewritePromptUser("rpu");
        cm.setQueryUnderstandModelId("qu");
        cm.setFaqPriorityEnabled(true);
        cm.setFaqDirectAnswerThreshold(0.9);
        cm.setFaqScoreBoost(1.2);
        cm.setDataAnalysisEnabled(true);
        cm.setImages(new ArrayList<>(List.of("img1")));
        cm.setVlmModelId("vlm");
        cm.setChatModelSupportsVision(true);
        // 载荷视图：未设置的字段取零值（与实体默认一致，渲染结果逐字节不变）
        cm.setAttachments(new ArrayList<>(List.of(new PipelineMessageAttachmentView(
                "a.txt", ".txt", 0, null, "hello", false, 0, 0, 0))));
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("greeting", "gp");
        cm.setIntentPromptOverrides(overrides);
        cm.setTenantId(7);
        cm.setWebSearchEnabled(true);
        cm.setWebSearchProviderId("prov");
        cm.setWebSearchMaxResults(5);
        cm.setWebFetchEnabled(true);
        cm.setWebFetchTopN(2);
        cm.setLanguage("zh");
        cm.getQueryPlan().setRewriteQuery("rq");
        cm.getQueryPlan().setIntent(QueryIntent.KB_SEARCH);
        cm.getQueryPlan().setImageDescription("desc");
        cm.getQueryPlan().setQuotedContext("qc");
        cm.getQueryPlan().setSystemPromptOverride("spo");
        cm.getQueryPlan().setMemoryPrompt("mp");
        cm.setUsedMemories(new ArrayList<>(List.of(
                new PipelineUsedMemoryView("m1", "fact", "c"))));
        cm.getEntityContext().setEntity(new ArrayList<>(List.of("e1")));
        cm.getEntityContext().setEntityKbIds(new ArrayList<>(List.of("kb1")));
        Map<String, String> ek = new LinkedHashMap<>();
        ek.put("k1", "kb1");
        cm.getEntityContext().setEntityKnowledge(ek);
        cm.getAnswer().setRenderedContexts("rc");
        return cm;
    }

    @Test
    void chatManageNeedsRetrievalMatrix() {
        List<Map<String, Object>> nr = new ArrayList<>();
        String[][] cases = {
                {QueryIntent.KB_SEARCH, "false"}, {QueryIntent.KB_SEARCH, "true"},
                {QueryIntent.WEB_SEARCH, "false"}, {QueryIntent.WEB_SEARCH, "true"},
                {QueryIntent.GREETING, "false"}, {QueryIntent.CHITCHAT, "false"},
                {QueryIntent.FOLLOW_UP, "false"}, {QueryIntent.IMAGE_ONLY, "false"},
                {QueryIntent.DOC_ONLY, "false"}, {QueryIntent.SUMMARIZE, "false"},
                {QueryIntent.CLARIFICATION, "false"}, {"", "false"},
        };
        for (String[] c : cases) {
            ChatManage m = new ChatManage();
            m.setWebSearchEnabled(Boolean.parseBoolean(c[1]));
            m.getQueryPlan().setIntent(c[0]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("intent", c[0]);
            row.put("web", Boolean.parseBoolean(c[1]));
            row.put("needs", m.needsRetrieval());
            nr.add(row);
        }
        assertRec("chat_manage", "needs_retrieval", json(nr));
    }

    @Test
    void chatManageCitationsEnabled() {
        List<Boolean> cn = new ArrayList<>();
        cn.add(new ChatManage().citationsEnabled());
        ChatManage cmT = new ChatManage();
        cmT.setCitationEnabled(true);
        cn.add(cmT.citationsEnabled());
        ChatManage cmF = new ChatManage();
        cmF.setCitationEnabled(false);
        cn.add(cmF.citationsEnabled());
        assertRec("chat_manage", "citations_enabled", json(cn));
    }
}
