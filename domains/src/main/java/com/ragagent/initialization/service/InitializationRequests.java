package com.ragagent.initialization.service;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.knowledge.config.ChunkingConfigView;
import com.ragagent.platform.web.JsonBodyErrors;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.platform.web.RequestFields;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** initialization 端点的原始请求体绑定器（record + 手写绑定,契约换锚时一并 DTO 化）。 */
public final class InitializationRequests {

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private InitializationRequests() {
    }
    record KBModelConfigRequest(String llmModelId, String embeddingModelId,
            JsonNode vlmConfig, JsonNode asrConfig, int chunkSize, int chunkOverlap,
            List<String> separators, List<ChunkingConfigView.ParserEngineRuleView>
            parserEngineRules, boolean enableParentChild, Integer parentChunkSize,
            Integer childChunkSize, String strategy, Integer tokenLimit, List<String> languages,
            String tableMetadataInstructions, boolean multimodalEnabled, String storageProvider,
            String storageBackendId, String name, String description,
            JsonNode faqConfig, JsonNode wikiConfig, JsonNode autoTagConfig, JsonNode indexingStrategy,
            boolean nodeExtractEnabled, String nodeExtractText, List<String> nodeExtractTags,
            List<Object> nodeExtractNodes, List<Object> nodeExtractRelations,
            String nodeExtractCustomInstructions, boolean questionGenerationEnabled,
            int questionGenerationCount, String questionGenerationInstructions) {}
    static KBModelConfigRequest bindKBModelConfigRequest(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            throw new BizException(AppError.badRequest(JsonBodyErrors.EMPTY_BODY));
        }
        JsonNode n;
        try {
            n = MAPPER.readTree(rawBody);
        } catch (Exception e) {
            throw new BizException(AppError.badRequest(
                    e.getMessage()));
        }
        if (n == null || !n.isObject()) {
            throw new BizException(AppError.badRequest(JsonBodyErrors.EMPTY_BODY));
        }
        String llmModelId = ModelConnectivityTestService.text(n, "llmModelId");
        if (llmModelId.isEmpty()) {
            throw new BizException(AppError.badRequest(
                    RequestFields.message("LLMModelID", "required")));
        }
        JsonNode ds = n.get("documentSplitting");
        JsonNode ne = n.get("nodeExtract");
        JsonNode qg = n.get("questionGeneration");
        List<String> seps = new ArrayList<>();
        if (ds != null && ds.get("separators") != null && ds.get("separators").isArray()) {
            ds.get("separators").forEach(s -> seps.add(s.asString()));
        }
        List<ChunkingConfigView.ParserEngineRuleView> rules = new ArrayList<>();
        if (ds != null && ds.get("parserEngineRules") != null && ds.get("parserEngineRules").isArray()) {
            for (JsonNode r : ds.get("parserEngineRules")) {
                rules.add(new ChunkingConfigView.ParserEngineRuleView(
                        toStringList(r.get("fileTypes")),
                        r.path("engine").asString(""),
                        r.path("xlsxFirstRowAsHeader").asBoolean(false)));
            }
        }
        return new KBModelConfigRequest(
                llmModelId,
                ModelConnectivityTestService.text(n, "embeddingModelId"),
                n.get("vlmConfig"),
                n.get("asrConfig"),
                ds == null ? 0 : ds.path("chunkSize").asInt(0),
                ds == null ? 0 : ds.path("chunkOverlap").asInt(0),
                seps,
                rules,
                ds != null && ds.path("enableParentChild").asBoolean(false),
                ds != null && ds.hasNonNull("parentChunkSize") ? ds.path("parentChunkSize").asInt() : null,
                ds != null && ds.hasNonNull("childChunkSize") ? ds.path("childChunkSize").asInt() : null,
                ds != null && ds.hasNonNull("strategy") ? ds.path("strategy").asString() : null,
                ds != null && ds.hasNonNull("tokenLimit") ? ds.path("tokenLimit").asInt() : null,
                ds != null && ds.hasNonNull("languages") ? toStringList(ds.get("languages")) : null,
                ds != null && ds.hasNonNull("tableMetadataInstructions")
                        ? ds.path("tableMetadataInstructions").asString() : null,
                n.path("multimodal").path("enabled").asBoolean(false),
                ModelConnectivityTestService.text(n, "storageProvider"),
                ModelConnectivityTestService.text(n, "storageBackendId"),
                ModelConnectivityTestService.text(n, "name"),
                n.hasNonNull("description") ? n.path("description").asString() : null,
                n.get("faqConfig"),
                n.get("wikiConfig"),
                n.get("autoTagConfig"),
                n.get("indexingStrategy"),
                ne != null && ne.path("enabled").asBoolean(false),
                ne == null ? "" : ne.path("text").asString(""),
                ne != null ? toStringList(ne.get("tags")) : List.of(),
                ne != null && ne.get("nodes") != null ? toList(ne.get("nodes")) : List.of(),
                ne != null && ne.get("relations") != null ? toList(ne.get("relations")) : List.of(),
                ne == null ? "" : ne.path("customInstructions").asString(""),
                qg != null && qg.path("enabled").asBoolean(false),
                qg == null ? 0 : qg.path("questionCount").asInt(0),
                qg == null ? "" : qg.path("customInstructions").asString(""));
    }
    static List<String> toStringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode e : n) {
                out.add(e.asString(""));
            }
        }
        return out;
    }

    static List<Object> toList(JsonNode n) {
        List<Object> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            n.forEach(out::add);
        }
        return out;
    }

}
