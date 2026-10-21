package com.ragagent.initialization.service;

import com.ragagent.contract.apikey.APIKeyScopeContext;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.knowledge.KnowledgeBaseResponse;
import com.ragagent.contract.knowledge.KnowledgeBaseRowView;
import com.ragagent.contract.knowledge.KnowledgeCatalogPort;
import com.ragagent.contract.knowledge.config.AsrConfigView;
import com.ragagent.contract.knowledge.config.ChunkingConfigView;
import com.ragagent.contract.knowledge.config.VlmConfigView;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.model.ModelView;
import com.ragagent.contract.tenant.TenantRole;
import com.ragagent.initialization.dto.InitResponses;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.config.AppEnvLookup;
import com.ragagent.platform.security.SsrfGuard;
import com.ragagent.platform.web.JsonBodies;
import com.ragagent.platform.web.JsonMappers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * initialization config/initialize 端点用例：KB 初始化配置读写、初始化装配与请求绑定。
 *
 * <p>initialization 薄层化的产物：端点注解留在 controller，方法体逐字迁移至此。</p>
 */
@Service
public final class InitializationConfigService {

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final KnowledgeCatalogPort knowledgeCatalog;
    private final ModelCatalogPort modelCatalog;
    private final SsrfGuard ssrfGuard;

    public InitializationConfigService(KnowledgeCatalogPort knowledgeCatalog,
            ModelCatalogPort modelCatalog, SsrfGuard ssrfGuard) {
        this.knowledgeCatalog = knowledgeCatalog;
        this.modelCatalog = modelCatalog;
        this.ssrfGuard = ssrfGuard;
    }

    // ── GET /initialization/config/:kbId ──

    public Map<String, Object> getConfig(String kbId) {
        knowledgeCatalog.requireKbAccess(kbId);
        KnowledgeBaseRowView kb = knowledgeCatalog.getAllTenantById(kbId);
        if (kb == null) {
            throw new BizException(AppError.notFound("知识库不存在"));
        }
        List<ModelView> models = new ArrayList<>();
        for (String id : List.of(JsonBodies.orEmpty(kb.getEmbeddingModelId()), JsonBodies.orEmpty(kb.getSummaryModelId()),
                JsonBodies.orEmpty(kb.getVlmConfig().modelId()))) {
            if (id.isEmpty()) {
                continue;
            }
            try {
                ModelView m = modelCatalog.getModelRow(id);
                if (m != null) {
                    models.add(m);
                }
            } catch (Exception ignored) {
                // 单个模型查询失败 → 跳过，继续处理其余模型
            }
        }
        return configResponse(models, kb, hasFiles(kbId));
    }

    // ── POST /initialization/initialize/:kbId ──

    public Map<String, Object> initialize(String kbId,
            String rawBody) {
        InitializationRequests.InitializationRequest req = InitializationRequests.bindInitializationRequest(rawBody);
        KnowledgeBaseRowView kb = kbForWrite(kbId);
        validateConfigs(req);

        List<ModelView> processed = new ArrayList<>();
        for (ModelDescriptor d : buildModelDescriptors(req)) {
            ModelView model = toModel(d);
            String existingId = findExistingModelId(kb, d.type());
            ModelView existing = null;
            if (!existingId.isEmpty()) {
                try {
                    existing = modelCatalog.getModelRow(existingId);
                } catch (Exception ignored) {
                    existing = null;
                }
            }
            if (existing != null) {
                existing.setName(model.getName());
                existing.setSource(model.getSource());
                existing.setDescription(model.getDescription());
                existing.setParameters(model.getParameters());
                modelCatalog.updateModel(existing);
                processed.add(existing);
            } else {
                modelCatalog.createModel(model);
                processed.add(model);
            }
        }
        applyInitialization(kb, req, processed);
        saveKb(kb);

        Map<String, Object> data = new TreeMap<>();
        data.put("knowledgeBase", KnowledgeBaseResponse.from(kb, knowledgeCatalog.retrieveDriver()));
        // 自有契约：api_key 留在 parameters、无 credentials 键
        data.put("models", processed.stream().map(InitResponses::rawModel).toList());
        data.put("message", "知识库配置更新成功");
        return data;
    }

    // ── PUT /initialization/config/:kbId ──

    public Map<String, Object> updateConfig(String kbId,
            String rawBody) {
        InitializationRequests.KBModelConfigRequest req = InitializationRequests.bindKBModelConfigRequest(rawBody);
        knowledgeCatalog.requireKbAccess(kbId);
        requireOwned(kbId);
        KnowledgeBaseRowView kb = knowledgeCatalog.getAllTenantById(kbId);
        if (kb == null) {
            throw new BizException(AppError.notFound("知识库不存在"));
        }

        // Embedding 变更 + 已有文件 → 400（先于模型校验）
        if (!kb.getEmbeddingModelId().isEmpty() && !req.embeddingModelId().isEmpty()
                && !kb.getEmbeddingModelId().equals(req.embeddingModelId())
                && hasFiles(kbId)) {
            throw new BizException(AppError.badRequest("知识库中已有文件，无法修改Embedding模型"));
        }
        requireModel(req.llmModelId(), "LLM模型不存在");
        if (!req.embeddingModelId().isEmpty()) {
            requireModel(req.embeddingModelId(), "Embedding模型不存在");
        }

        kb.setSummaryModelId(req.llmModelId());
        if (!req.embeddingModelId().isEmpty()) {
            kb.setEmbeddingModelId(req.embeddingModelId());
        }

        // VLM / ASR 重置后再按请求回填（收集为契约视图，末尾统一回写）
        boolean vlmEnabled = false;
        String vlmModelId = "";
        JsonNode vlmReq = req.vlmConfig();
        if (vlmReq != null && req.multimodalEnabled() && !vlmReq.path("modelId").asString("").isEmpty()) {
            String candidate = vlmReq.path("modelId").asString("");
            try {
                if (modelCatalog.findModel(candidate) != null) {
                    vlmEnabled = vlmReq.path("enabled").asBoolean(false);
                    vlmModelId = candidate;
                }
            } catch (Exception ignored) {
                // 查询失败 → 视为模型不存在，VLM 保持禁用
            }
        }
        if (!vlmEnabled) {
            vlmModelId = "";
        }
        boolean asrEnabled = false;
        String asrModelId = "";
        String asrLanguage = "";
        JsonNode asrReq = req.asrConfig();
        if (asrReq != null && asrReq.path("enabled").asBoolean(false)
                && !asrReq.path("modelId").asString("").isEmpty()) {
            try {
                if (modelCatalog.findModel(asrReq.path("modelId").asString()) != null) {
                    asrEnabled = true;
                    asrModelId = asrReq.path("modelId").asString();
                    asrLanguage = asrReq.path("language").asString("");
                }
            } catch (Exception ignored) {
                // 查询失败 → 忽略，ASR 保持未启用
            }
        }
        kb.setAsrConfig(new AsrConfigView(asrEnabled, asrModelId, asrLanguage));

        // 文档分块（未提供的键保持现值；未生效标注按最终值重算）
        ChunkingConfigView curChunking = kb.getChunkingConfig();
        int chunkSize = req.chunkSize() > 0 ? req.chunkSize() : curChunking.chunkSize();
        int chunkOverlap = req.chunkOverlap() >= 0 ? req.chunkOverlap() : curChunking.chunkOverlap();
        List<String> separators = req.separators() != null && !req.separators().isEmpty()
                ? req.separators() : curChunking.separators();
        List<ChunkingConfigView.ParserEngineRuleView> parserRules = req.parserEngineRules();
        boolean enableParentChild = req.enableParentChild();
        int parentChunkSize = req.parentChunkSize() != null && req.parentChunkSize() > 0
                ? req.parentChunkSize() : curChunking.parentChunkSize();
        int childChunkSize = req.childChunkSize() != null && req.childChunkSize() > 0
                ? req.childChunkSize() : curChunking.childChunkSize();
        String strategy = req.strategy() != null ? req.strategy() : curChunking.strategy();
        int tokenLimit = req.tokenLimit() != null ? req.tokenLimit() : curChunking.tokenLimit();
        List<String> languages = req.languages() != null ? req.languages() : curChunking.languages();
        String tableMetadata = req.tableMetadataInstructions() != null
                ? req.tableMetadataInstructions().trim() : curChunking.tableMetadataInstructions();
        kb.setChunkingConfig(new ChunkingConfigView(chunkSize, chunkOverlap, separators, parserRules,
                enableParentChild, parentChunkSize, childChunkSize, strategy, tokenLimit, languages,
                tableMetadata, ChunkingConfigView.inactiveKeys(enableParentChild, parentChunkSize,
                        childChunkSize, parserRules != null && !parserRules.isEmpty(), tableMetadata)));

        if (!req.multimodalEnabled()) {
            vlmModelId = "";
        }
        String vlmDescLang = null;
        String vlmCustomInstr = null;
        if (vlmReq != null) {
            vlmDescLang = vlmReq.path("descriptionLanguage").asString("").trim();
            vlmCustomInstr = vlmReq.path("customInstructions").asString("").trim();
        }
        kb.setVlmConfig(new VlmConfigView(vlmEnabled, vlmModelId, vlmDescLang, vlmCustomInstr,
                "", "", ""));

        // 存储引擎：provider 兼容投影
        String provider = req.storageProvider() == null ? "" : req.storageProvider().trim().toLowerCase();
        if (provider.isEmpty()) {
            provider = "local";
        }
        List<String> supported = List.of("local", "minio", "cos", "tos", "s3", "oss", "ks3", "obs");
        if (!supported.contains(provider)) {
            throw new BizException(AppError.badRequest("Storage provider is not allowed by STORAGE_ALLOW_LIST"));
        }
        kb.setStorageProvider(provider);

        // 知识图谱
        if (req.nodeExtractEnabled()) {
            ObjectNode extract = MAPPER.createObjectNode();
            extract.put("enabled", true);
            extract.put("text", req.nodeExtractText());
            extract.set("tags", MAPPER.valueToTree(req.nodeExtractTags()));
            extract.set("nodes", MAPPER.valueToTree(req.nodeExtractNodes()));
            extract.set("relations", MAPPER.valueToTree(req.nodeExtractRelations()));
            extract.put("customInstructions", req.nodeExtractCustomInstructions().trim());
            kb.setExtractConfig(extract);
        } else if (kb.getExtractConfig() != null) {
            ((ObjectNode) kb.getExtractConfig()).put("enabled", false);
        } else {
            ObjectNode extract = MAPPER.createObjectNode();
            extract.put("enabled", false);
            kb.setExtractConfig(extract);
        }

        // 问题生成
        ObjectNode qg = MAPPER.createObjectNode();
        if (req.questionGenerationEnabled()) {
            int count = req.questionGenerationCount();
            if (count <= 0) {
                count = 3;
            }
            if (count > 10) {
                count = 10;
            }
            qg.put("enabled", true);
            qg.put("questionCount", count);
            qg.put("customInstructions", req.questionGenerationInstructions().trim());
        } else {
            qg.put("enabled", false);
            qg.put("customInstructions", req.questionGenerationInstructions().trim());
        }
        kb.setQuestionGenerationConfig(qg);

        saveKb(kb);
        return java.util.Collections.singletonMap("message", "配置更新成功");
    }

    /** RFC-3339 纳秒精度 + 服务器本地时区（同 ZeroTimeSerializer 逻辑）。 */

    static Map<String, String> toStringMap(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        Map<String, String> out = new TreeMap<>();
        for (var e : n.properties()) {
            out.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asString(""));
        }
        return out;
    }

    // ── 绑定 ──





    // ── 守卫与校验 ──

    private KnowledgeBaseRowView kbForWrite(String kbId) {
        knowledgeCatalog.requireKbAccess(kbId);
        requireOwned(kbId);
        KnowledgeBaseRowView kb = knowledgeCatalog.getAllTenantById(kbId);
        if (kb == null) {
            throw new BizException(AppError.notFound("知识库不存在"));
        }
        return kb;
    }

    private void requireOwned(String kbId) {
        KnowledgeBaseRowView kb = knowledgeCatalog.getAllTenantById(kbId);
        if (kb == null) {
            throw new BizException(AppError.notFound("knowledge base not found"));
        }
        String role = TenantContext.currentRole();
        boolean admin = TenantRole.fromString(role == null ? "" : role)
                .hasPermission(TenantRole.ADMIN);
        String uid = TenantContext.currentUserId() == null ? "" : TenantContext.currentUserId();
        String creator = kb.getCreatorId() == null ? "" : kb.getCreatorId();
        if (!admin && (creator.isEmpty() || !creator.equals(uid))) {
            throw BizException.forbidden("must own the resource or have the required role");
        }
    }

    private void validateConfigs(InitializationRequests.InitializationRequest req) {
        List<String[]> urls = new ArrayList<>();
        urls.add(new String[] {"LLM BaseURL", req.llmBaseUrl()});
        urls.add(new String[] {"Embedding BaseURL", req.embBaseUrl()});
        urls.add(new String[] {"Rerank BaseURL", req.rerankBaseUrl()});
        if (req.multimodal() != null && req.multimodal().get("vlm") != null) {
            urls.add(new String[] {"VLM BaseURL", req.multimodal().path("vlm").path("baseUrl").asString("")});
        }
        for (String[] u : urls) {
            if (!u[1].isEmpty()) {
                try {
                    ssrfGuard.validateURLForSSRF(u[1]);
                } catch (Exception e) {
                    throw new BizException(AppError.badRequest(
                            ssrfGuard.formatSSRFError(u[0], u[1], e)));
                }
            }
        }
        if (req.multimodalEnabled()) {
            JsonNode vlm = req.multimodal() == null ? null : req.multimodal().get("vlm");
            if (vlm == null) {
                throw new BizException(AppError.badRequest("启用多模态时需要配置VLM信息"));
            }
            String modelName = vlm.path("modelName").asString("");
            String baseUrl = vlm.path("baseUrl").asString("");
            if ("ollama".equals(vlm.path("interfaceType").asString(""))) {
                String ollama = AppEnvLookup.get("OLLAMA_BASE_URL");
                baseUrl = (ollama == null ? "" : ollama) + "/v1";
            }
            if (modelName.isEmpty() || baseUrl.isEmpty()) {
                throw new BizException(AppError.badRequest("VLM配置不完整"));
            }
        }
        if (req.rerankEnabled() && (req.rerankModelName().isEmpty() || req.rerankBaseUrl().isEmpty())) {
            throw new BizException(AppError.badRequest("Rerank配置不完整"));
        }
        if (req.nodeExtractEnabled()) {
            String neo4j = AppEnvLookup.get("NEO4J_ENABLE");
            if (!"true".equalsIgnoreCase(neo4j == null ? "" : neo4j)) {
                throw new BizException(AppError.badRequest("请正确配置环境变量NEO4J_ENABLE"));
            }
            if (req.nodeExtractText().isEmpty() || req.nodeExtractTags().isEmpty()) {
                throw new BizException(AppError.badRequest("Node Extractor配置不完整"));
            }
            if (req.nodeExtractNodes().isEmpty() || req.nodeExtractRelations().isEmpty()) {
                throw new BizException(AppError.badRequest("请先提取实体和关系"));
            }
        }
    }

    // ── 模型处理 ──

    private record ModelDescriptor(String type, String name, String source, String description,
            String baseUrl, String apiKey, int dimension, String interfaceType) {}

    private List<ModelDescriptor> buildModelDescriptors(InitializationRequests.InitializationRequest req) {
        List<ModelDescriptor> list = new ArrayList<>();
        list.add(new ModelDescriptor("KnowledgeQA", req.llmModelName(), req.llmSource(),
                "LLM Model for Knowledge QA", req.llmBaseUrl(), req.llmApiKey(), 0, ""));
        list.add(new ModelDescriptor("Embedding", req.embModelName(), req.embSource(),
                "Embedding Model", req.embBaseUrl(), req.embApiKey(), req.embDimension(), ""));
        if (req.rerankEnabled()) {
            list.add(new ModelDescriptor("Rerank", req.rerankModelName(), "remote",
                    "Rerank Model", req.rerankBaseUrl(), req.rerankApiKey(), 0, ""));
        }
        if (req.multimodalEnabled() && req.multimodal() != null && req.multimodal().get("vlm") != null) {
            JsonNode vlm = req.multimodal().get("vlm");
            list.add(new ModelDescriptor("VLLM", vlm.path("modelName").asString(""), "remote",
                    "VLM Model", vlm.path("baseUrl").asString(""), vlm.path("apiKey").asString(""),
                    0, vlm.path("interfaceType").asString("")));
        }
        return list;
    }

    private ModelView toModel(ModelDescriptor d) {
        ModelView m = new ModelView();
        m.setType(d.type());
        m.setName(d.name());
        m.setSource(d.source());
        m.setDescription(d.description());
        ModelParameters p = new ModelParameters();
        p.setBaseUrl(d.baseUrl());
        p.setApiKey(d.apiKey());
        p.setInterfaceType(d.interfaceType());
        if ("Embedding".equals(d.type())) {
            p.getEmbeddingParameters().setDimension(d.dimension());
        }
        m.setParameters(p);
        m.setDisplayName("");
        m.setIsDefault(false);
        m.setStatus("active");
        // 租户不回填 → 行落 tenant_id=0，
        // 后续按租户回读找不到（golden init-get-config-after 无 llm/embedding 键即此因）
        m.setTenantId(0L);
        return m;
    }

    private String findExistingModelId(KnowledgeBaseRowView kb, String type) {
        return switch (type) {
            case "Embedding" -> JsonBodies.orEmpty(kb.getEmbeddingModelId());
            case "KnowledgeQA" -> JsonBodies.orEmpty(kb.getSummaryModelId());
            case "VLLM" -> JsonBodies.orEmpty(kb.getVlmConfig().modelId());
            default -> "";
        };
    }

    private void applyInitialization(KnowledgeBaseRowView kb, InitializationRequests.InitializationRequest req,
            List<ModelView> processed) {
        String embeddingId = "";
        String llmId = "";
        String vlmId = "";
        for (ModelView m : processed) {
            switch (m.getType()) {
                case "Embedding" -> embeddingId = m.getId();
                case "KnowledgeQA" -> llmId = m.getId();
                case "VLLM" -> vlmId = m.getId();
                default -> {
                }
            }
        }
        kb.setSummaryModelId(llmId);
        kb.setEmbeddingModelId(embeddingId);
        ChunkingConfigView prevChunking = kb.getChunkingConfig();
        kb.setChunkingConfig(new ChunkingConfigView(req.chunkSize(), req.chunkOverlap(),
                req.separators(), prevChunking.parserEngineRules(), prevChunking.enableParentChild(),
                prevChunking.parentChunkSize(), prevChunking.childChunkSize(), prevChunking.strategy(),
                prevChunking.tokenLimit(), prevChunking.languages(),
                prevChunking.tableMetadataInstructions(),
                ChunkingConfigView.inactiveKeys(prevChunking.enableParentChild(),
                        prevChunking.parentChunkSize(), prevChunking.childChunkSize(),
                        prevChunking.parserEngineRules() != null && !prevChunking.parserEngineRules().isEmpty(),
                        prevChunking.tableMetadataInstructions())));

        if (req.multimodalEnabled()) {
            kb.setVlmConfig(new VlmConfigView(true, vlmId, null, null, "", "", ""));
            String storageType = req.multimodal() == null ? ""
                    : req.multimodal().path("storageType").asString("");
            // cos/minio 凭据落库段依赖部署环境（dev 无），只在段存在时触达
            if (("cos".equals(storageType) || "minio".equals(storageType))
                    && req.multimodal().get(storageType) != null) {
                kb.setStorageProvider(storageType);
            }
        } else {
            kb.setVlmConfig(new VlmConfigView(false, "", null, null, "", "", ""));
            kb.setStorageProvider("");
        }

        if (req.nodeExtractEnabled()) {
            ObjectNode extract = MAPPER.createObjectNode();
            extract.put("text", req.nodeExtractText());
            extract.set("tags", MAPPER.valueToTree(req.nodeExtractTags()));
            var nodes = extract.putArray("nodes");
            for (Object n : req.nodeExtractNodes()) {
                JsonNode node = MAPPER.valueToTree(n);
                ObjectNode copy = nodes.addObject();
                copy.put("name", node.path("name").asString(""));
                copy.set("attributes", node.get("attributes") == null
                        ? MAPPER.createArrayNode() : node.get("attributes").deepCopy());
            }
            var relations = extract.putArray("relations");
            for (Object r : req.nodeExtractRelations()) {
                JsonNode rel = MAPPER.valueToTree(r);
                ObjectNode copy = relations.addObject();
                copy.put("node1", rel.path("node1").asString(""));
                copy.put("node2", rel.path("node2").asString(""));
                copy.put("type", rel.path("type").asString(""));
            }
            kb.setExtractConfig(extract);
        }
    }

    private void saveKb(KnowledgeBaseRowView kb) {
        knowledgeCatalog.updateKnowledgeBase(kb);
    }

    // ── GET config 响应（全 map 字母序） ──

    private Map<String, Object> configResponse(List<ModelView> models, KnowledgeBaseRowView kb,
            boolean hasFiles) {
        Map<String, Object> config = new TreeMap<>();
        config.put("hasFiles", hasFiles);
        boolean canView = canViewIntegrationSecrets();

        for (ModelView m : models) {
            String baseUrl = m.getParameters() == null ? "" : m.getParameters().getBaseUrl();
            if (m.isIsBuiltin() || !canView) {
                baseUrl = "";
            }
            boolean hasKey = m.getParameters() != null && !m.getParameters().getApiKey().isEmpty()
                    && !m.isIsBuiltin();
            switch (m.getType()) {
                case "KnowledgeQA" -> config.put("llm", sortedBlock(Map.of(
                        "source", JsonBodies.orEmpty(m.getSource()),
                        "modelName", JsonBodies.orEmpty(m.getName()),
                        "baseUrl", baseUrl,
                        "credentials", Map.of("apiKey", hasKey))));
                case "Embedding" -> config.put("embedding", sortedBlock(Map.of(
                        "source", JsonBodies.orEmpty(m.getSource()),
                        "modelName", JsonBodies.orEmpty(m.getName()),
                        "baseUrl", baseUrl,
                        "dimension", m.getParameters() == null ? 0
                                : m.getParameters().getEmbeddingParameters().getDimension(),
                        "credentials", Map.of("apiKey", hasKey))));
                case "Rerank" -> config.put("rerank", sortedBlock(Map.of(
                        "enabled", true,
                        "modelName", JsonBodies.orEmpty(m.getName()),
                        "baseUrl", baseUrl,
                        "credentials", Map.of("apiKey", hasKey))));
                case "VLLM" -> {
                    Map<String, Object> mm = castMap(config.get("multimodal"));
                    if (mm == null) {
                        mm = new TreeMap<>();
                        mm.put("enabled", true);
                        config.put("multimodal", mm);
                    }
                    mm.put("vlm", sortedBlock(Map.of(
                            "modelName", JsonBodies.orEmpty(m.getName()),
                            "baseUrl", baseUrl,
                            "interfaceType", m.getParameters() == null ? ""
                                    : m.getParameters().getInterfaceType(),
                            "modelId", m.getId(),
                            "credentials", Map.of("apiKey", hasKey))));
                }
                default -> {
                }
            }
        }

        String storageProvider = kb.getStorageProvider();
        boolean hasMultimodal = kb.getVlmConfig().enabled()
                || !kb.getStorageConfig().secretId().isEmpty()
                || !kb.getStorageConfig().bucketName().isEmpty()
                || (!storageProvider.isEmpty() && !"local".equals(storageProvider));
        Map<String, Object> mm = castMap(config.get("multimodal"));
        if (mm == null) {
            mm = new TreeMap<>();
            mm.put("enabled", hasMultimodal);
            config.put("multimodal", mm);
        } else {
            mm.put("enabled", hasMultimodal);
        }
        String descLang = kb.getVlmConfig().descriptionLanguage();
        String customInstr = kb.getVlmConfig().customInstructions();
        if ((descLang != null && !descLang.isEmpty()) || (customInstr != null && !customInstr.isEmpty())) {
            if (descLang != null && !descLang.isEmpty()) {
                mm.put("descriptionLanguage", descLang);
            }
            if (customInstr != null && !customInstr.isEmpty()) {
                mm.put("customInstructions", customInstr);
            }
        }

        if (config.get("rerank") == null) {
            config.put("rerank", sortedBlock(Map.of(
                    "enabled", false,
                    "modelName", "",
                    "baseUrl", "",
                    "credentials", Map.of("apiKey", false))));
        }

        var c = kb.getChunkingConfig();
        Map<String, Object> ds = new TreeMap<>();
        ds.put("chunkSize", c.chunkSize());
        ds.put("chunkOverlap", c.chunkOverlap());
        ds.put("separators", c.separators());
        if (c.strategy() != null && !c.strategy().isEmpty()) {
            ds.put("strategy", c.strategy());
        }
        if (c.tokenLimit() > 0) {
            ds.put("tokenLimit", c.tokenLimit());
        }
        if (c.languages() != null && !c.languages().isEmpty()) {
            ds.put("languages", c.languages());
        }
        if (c.tableMetadataInstructions() != null && !c.tableMetadataInstructions().isEmpty()) {
            ds.put("tableMetadataInstructions", c.tableMetadataInstructions());
        }
        config.put("documentSplitting", ds);

        String effectiveProvider = kb.getStorageProvider();
        if (!kb.getStorageConfig().secretId().isEmpty()
                || (!effectiveProvider.isEmpty() && !"local".equals(effectiveProvider))) {
            Map<String, Object> mm2 = castMap(config.get("multimodal"));
            if (mm2 == null) {
                mm2 = new TreeMap<>();
                mm2.put("enabled", true);
                config.put("multimodal", mm2);
            }
            mm2.put("storageType", effectiveProvider);
            if ("cos".equals(effectiveProvider)) {
                Map<String, Object> cos = new TreeMap<>();
                cos.put("region", kb.getStorageConfig().region());
                cos.put("bucketName", kb.getStorageConfig().bucketName());
                cos.put("appId", kb.getStorageConfig().appId());
                cos.put("pathPrefix", kb.getStorageConfig().pathPrefix());
                cos.put("credentials", Map.of(
                        "secretId", !kb.getStorageConfig().secretId().isEmpty(),
                        "secretKey", !kb.getStorageConfig().secretKey().isEmpty()));
                mm2.put("cos", cos);
            } else if ("minio".equals(effectiveProvider)) {
                mm2.put("minio", sortedBlock(Map.of(
                        "bucketName", kb.getStorageConfig().bucketName(),
                        "pathPrefix", kb.getStorageConfig().pathPrefix())));
            }
        }

        JsonNode extract = JsonMappers.toJsonNode(kb.getExtractConfig());
        if (extract != null) {
            Map<String, Object> ne = new TreeMap<>();
            ne.put("enabled", extract.path("enabled").asBoolean(false));
            ne.put("text", extract.path("text").asString(""));
            ne.put("tags", extract.get("tags"));
            ne.put("nodes", extract.get("nodes"));
            ne.put("relations", extract.get("relations"));
            String ci = extract.path("customInstructions").asString("");
            if (!ci.isEmpty()) {
                ne.put("customInstructions", ci);
            }
            config.put("nodeExtract", ne);
        } else {
            config.put("nodeExtract", sortedBlock(Map.of("enabled", false)));
        }

        JsonNode qg = JsonMappers.toJsonNode(kb.getQuestionGenerationConfig());
        if (qg != null) {
            config.put("questionGeneration", sortedBlock(Map.of(
                    "enabled", qg.path("enabled").asBoolean(false),
                    "questionCount", qg.path("questionCount").asInt(0),
                    "customInstructions", qg.path("customInstructions").asString(""))));
        } else {
            config.put("questionGeneration", sortedBlock(Map.of("enabled", false)));
        }
        return config;
    }

    // ── 小工具 ──

    private boolean hasFiles(String kbId) {
        return knowledgeCatalog.hasFiles(kbId);
    }

    private void requireModel(String id, String message) {
        if (id == null || id.isEmpty()) {
            throw new BizException(AppError.badRequest(message));
        }
        try {
            ModelView m = modelCatalog.getModelRow(id);
            if (m != null) {
                return;
            }
        } catch (Exception ignored) {
            // 落到下方 400
        }
        throw new BizException(AppError.badRequest(message));
    }

    private boolean canViewIntegrationSecrets() {
        String role = TenantContext.currentRole();
        if (TenantRole.fromString(role == null ? "" : role).hasPermission(TenantRole.ADMIN)) {
            return true;
        }
        var scope = APIKeyScopeContext.current();
        return scope != null && (scope.fullAccess() || scope.hasCapability("manage_tenant_settings"));
    }

    /** Map.of 乱序 → TreeMap 重排（响应键为字母序）。 */
    private static Map<String, Object> sortedBlock(Map<String, Object> entries) {
        return new TreeMap<>(entries);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }



}
