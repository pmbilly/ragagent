package com.ragagent.model.service;

import com.ragagent.common.context.TenantContext;
import com.ragagent.common.crypto.CryptoService;
import com.ragagent.common.error.BizException;
import com.ragagent.common.security.SsrfGuard;
import com.ragagent.embedding.Embedder;
import com.ragagent.embedding.EmbedderFactory;
import com.ragagent.llm.LlmChatClient;
import com.ragagent.llm.chat.LlmChatClients;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.model.domain.Model;
import com.ragagent.model.domain.ModelParameters;
import com.ragagent.model.service.ModelService.ModelNotFoundException;
import com.ragagent.rerank.Reranker;
import com.ragagent.rerank.RerankerFactory;
import com.ragagent.retrieval.vlm.VlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 模型运行时工厂（chat / embedding / rerank / vlm / asr 五类运行时客户端的装配归口，
 * 服务于 models/{id}/debug 端点与 agent 引擎装配）。
 *
 * <p>两类取数口径（差别是契约，别统一）：</p>
 * <ul>
 *   <li><b>embedding / rerank</b> 走 {@code getModelGated}（带状态闸门：
 *       downloading → "model is currently downloading" 等）；</li>
 *   <li><b>chat / vlm / asr</b> 走 {@code getModelDirect} 直取（无状态闸门）。</li>
 * </ul>
 *
 * <p>错误形态：调用方（DebugModel）把异常消息写进 {@code data.error}（HTTP 200）。
 * 因此本类抛出的 {@link RuntimeException} 的 {@code getMessage()} 即对外文案
 * （错误文案是契约）；底层部件抛 {@link BizException} 时在此拆包取其 message
 * （BizException 的 getMessage 带 "error code: ..." 前缀，不能直接当对外文案用）。</p>
 */
@Component
public class ModelRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(ModelRuntimeFactory.class);

    private final ModelService modelService;
    private final CryptoService cryptoService;
    private final ObjectProvider<OllamaService> ollamaService;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final SsrfGuard ssrfGuard;

    public ModelRuntimeFactory(ModelService modelService,
                               CryptoService cryptoService, ObjectProvider<OllamaService> ollamaService,
                               ConcurrencyGovernor concurrencyGovernor, SsrfGuard ssrfGuard) {
        this.modelService = modelService;
        this.cryptoService = cryptoService;
        this.ollamaService = ollamaService;
        this.concurrencyGovernor = concurrencyGovernor;
        this.ssrfGuard = ssrfGuard;
    }

    /** repo 直取（无状态闸门）→ chat 客户端装配。 */
    public LlmChatClient getChatModel(String modelId) {
        Model model = getModelDirect(modelId);
        log.info("Getting chat model: {}, source: {}", model.getName(), model.getSource());
        String[] creds = modelCredentials(model.getParameters());
        try {
            // langfuse generation 装饰（未启用时原样返回，零成本）
            return com.ragagent.tracing.langfuse.LangfuseChatClient.wrap(
                    LlmChatClients.create(ModelRuntimeConfigs.chatConfig(model, creds[0], creds[1]),
                            ollamaService.getIfAvailable(), concurrencyGovernor));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** GetModelByID（状态闸门）→ embedder 工厂装配。 */
    public Embedder getEmbeddingModel(String modelId) {
        Model model = getModelGated(modelId);
        log.info("Getting embedding model: {}, source: {}", model.getName(), model.getSource());
        String[] creds = modelCredentials(model.getParameters());
        try {
            // pooler 只服务批量向量化；debug 只走单文本 embed，传 null
            // langfuse generation 装饰
            return com.ragagent.tracing.langfuse.LangfuseEmbedder.wrap(
                    EmbedderFactory.newEmbedder(
                            ModelRuntimeConfigs.embedderConfig(model, creds[0], creds[1]),
                            null, ollamaService.getIfAvailable(), concurrencyGovernor));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** GetModelByID（状态闸门）→ reranker 工厂装配。 */
    public Reranker getRerankModel(String modelId) {
        Model model = getModelGated(modelId);
        log.info("Getting rerank model: {}, source: {}", model.getName(), model.getSource());
        String[] creds = modelCredentials(model.getParameters());
        try {
            // langfuse generation 装饰
            return com.ragagent.tracing.langfuse.LangfuseReranker.wrap(
                    RerankerFactory.newReranker(
                            ModelRuntimeConfigs.rerankerConfig(model, creds[0], creds[1])));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** repo 直取（无状态闸门）。调用方再 configFromModel + predict。 */
    public Model getVlmModel(String modelId) {
        Model model = getModelDirect(modelId);
        log.info("Getting VLM model: {}, source: {}", model.getName(), model.getSource());
        return model;
    }

    /**
     * VLM 客户端配置：构造 + 非 ollama 的基址 SSRF 校验
     * （validateVLMBaseURL；ollama 不校验基址）。
     *
     * <p>失败抛 {@link RuntimeException}，message 即对外错误文案——调用方
     * （模型调试端点、agent 引擎装配）直接写进 {@code data.error}。</p>
     */
    public VlmClient.VlmConfig vlmConfigFor(Model model) {
        VlmClient.VlmConfig config = ModelRuntimeConfigs.vlmConfig(model);
        if (!config.isOllama()) {
            validateVlmBaseUrl(config.baseUrl());
        }
        return config;
    }

    /** repo 直取（无状态闸门）。调用方再组 AsrConfig + transcribe。 */
    public Model getAsrModel(String modelId) {
        Model model = getModelDirect(modelId);
        log.info("Getting ASR model: {}, source: {}", model.getName(), model.getSource());
        return model;
    }

    /**
     * 构造期校验（validateVLMBaseURL）：SSRF 失败文案
     * "base URL SSRF check failed: ..."。
     */
    public void validateVlmBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return;
        }
        try {
            ssrfGuard.validateURLForSSRF(baseUrl);
        } catch (RuntimeException e) {
            throw new RuntimeException("base URL SSRF check failed: " + e.getMessage());
        }
    }

    // ── 取数口径 ─────────────────────────────────────────────────────────

    /** 按 ID 直取（tenant 可见性含 is_builtin；无状态闸门）。 */
    private Model getModelDirect(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            throw new RuntimeException("model ID cannot be empty");
        }
        long tid = TenantContext.currentTenantId() == null ? 0 : TenantContext.currentTenantId();
        Model model = modelService.getByIdVisible(tid, modelId);
        if (model == null) {
            throw new RuntimeException("model not found");
        }
        return model;
    }

    /** 带状态闸门取模型，错误文案拆包为对外原文。 */
    private Model getModelGated(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            throw new RuntimeException("model ID cannot be empty");
        }
        try {
            return modelService.getModelByID(modelId);
        } catch (ModelNotFoundException e) {
            throw new RuntimeException("model not found");
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    // ── 模型级凭证 ───────────────────────────────────────────────────────

    /** 模型级 appId/appSecret（通用凭据承载；cloud provider 已裁撤，无租户回落）。 */
    private String[] modelCredentials(ModelParameters params) {
        String appId = params == null || params.getAppId() == null ? "" : params.getAppId();
        String appSecret = decryptAppSecret(params == null ? null : params.getAppSecret());
        return new String[] {appId, appSecret};
    }

    /** 空原样返回；宽容解密（失败原样返回）。 */
    private String decryptAppSecret(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }
        var decrypted = cryptoService.decryptStoredSecretLenient(encrypted);
        return decrypted.ok() ? decrypted.plaintext() : encrypted;
    }
}
