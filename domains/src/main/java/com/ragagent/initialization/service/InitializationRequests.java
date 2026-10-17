package com.ragagent.initialization.service;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.knowledge.domain.ChunkingConfigView;
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
    record InitializationRequest(String llmSource, String llmModelName, String llmBaseUrl,
            String llmApiKey, String embSource, String embModelName, String embBaseUrl,
            String embApiKey, int embDimension, boolean rerankEnabled, String rerankModelName,
            String rerankBaseUrl, String rerankApiKey, boolean multimodalEnabled,
            JsonNode multimodal, int chunkSize, int chunkOverlap, List<String> separators,
            boolean nodeExtractEnabled, String nodeExtractText, List<String> nodeExtractTags,
            List<Object> nodeExtractNodes, List<Object> nodeExtractRelations) {}
    static InitializationRequest bindInitializationRequest(String rawBody) {
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
        JsonNode llm = n.get("llm");
        JsonNode emb = n.get("embedding");
        JsonNode ds = n.get("documentSplitting");
        // gin binding 按 struct 字段序报第一个错误
        if (llm == null || llm.path("source").asString("").isEmpty()) {
            throw required("InitializationRequest.LLM.Source", "Source");
        }
        if (llm.path("modelName").asString("").isEmpty()) {
            throw required("InitializationRequest.LLM.ModelName", "ModelName");
        }
        if (emb == null || emb.path("source").asString("").isEmpty()) {
            throw required("InitializationRequest.Embedding.Source", "Source");
        }
        if (emb.path("modelName").asString("").isEmpty()) {
            throw required("InitializationRequest.Embedding.ModelName", "ModelName");
        }
        int chunkSize = 0;
        int chunkOverlap = 0;
        List<String> seps = new ArrayList<>();
        if (ds == null) {
            throw new BizException(AppError.badRequest(
                    RequestFields.message("DocumentSplitting", "required")));
        }
        chunkSize = ds.path("chunkSize").asInt(0);
        chunkOverlap = ds.path("chunkOverlap").asInt(0);
        if (ds.get("separators") != null && ds.get("separators").isArray()) {
            ds.get("separators").forEach(s -> seps.add(s.asString()));
        }
        if (chunkSize < 100) {
            throw new BizException(AppError.badRequest(
                    RequestFields.message("ChunkSize", "min")));
        }
        if (chunkSize > 10000) {
            throw new BizException(AppError.badRequest(
                    RequestFields.message("ChunkSize", "max")));
        }
        if (seps.isEmpty()) {
            throw new BizException(AppError.badRequest(
                    RequestFields.message("Separators", "min")));
        }
        JsonNode mm = n.get("multimodal");
        JsonNode ne = n.get("nodeExtract");
        return new InitializationRequest(
                llm.path("source").asString(""), llm.path("modelName").asString(""),
                llm.path("baseUrl").asString(""), llm.path("apiKey").asString(""),
                emb.path("source").asString(""), emb.path("modelName").asString(""),
                emb.path("baseUrl").asString(""), emb.path("apiKey").asString(""),
                emb.path("dimension").asInt(0),
                n.path("rerank").path("enabled").asBoolean(false),
                n.path("rerank").path("modelName").asString(""),
                n.path("rerank").path("baseUrl").asString(""),
                n.path("rerank").path("apiKey").asString(""),
                mm != null && mm.path("enabled").asBoolean(false),
                mm,
                chunkSize, chunkOverlap, seps,
                ne != null && ne.path("enabled").asBoolean(false),
                ne == null ? "" : ne.path("text").asString(""),
                ne != null ? toStringList(ne.get("tags")) : List.of(),
                ne != null && ne.get("nodes") != null ? toList(ne.get("nodes")) : List.of(),
                ne != null && ne.get("relations") != null ? toList(ne.get("relations")) : List.of());
    }
    static BizException required(String structField, String field) {
        return new BizException(AppError.badRequest(RequestFields.message(field, "required")));
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
