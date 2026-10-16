package com.ragagent.modelruntime.application;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.model.Embedder;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.model.Reranker;
import com.ragagent.contract.model.VlmConfig;
import com.ragagent.embedding.EmbedderFactory;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.security.CryptoService;
import com.ragagent.platform.security.SsrfGuard;
import com.ragagent.platform.tracing.decorators.LangfuseChatClient;
import com.ragagent.platform.tracing.decorators.LangfuseEmbedder;
import com.ragagent.platform.tracing.decorators.LangfuseReranker;
import com.ragagent.rerank.RerankerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 模型运行时装配（{@link ModelRuntime} 的实现）：chat / embedding / rerank / vlm / asr 五类
 * 运行时客户端的构造归口，服务 {@code models/{id}/debug} 端点与 agent 引擎装配。
 *
 * <p><b>为什么在 model-runtime</b>：构造要用的都是本模块的部件（{@code LlmChatClients} /
 * {@code EmbedderFactory} / {@code RerankerFactory} / {@code OllamaService} / 限流器）；
 * 只有"模型行"与"凭据解密"是外部能力——前者走契约端口 {@link ModelCatalogPort}、
 * 后者走平台层 {@link CryptoService}。这样消费域只 import 契约面，不必看到本模块实现。</p>
 *
 * <p>两类取数口径（差别是契约，别统一）：</p>
 * <ul>
 *   <li><b>embedding / rerank</b> 走 {@code findModelOrEmpty}（带状态闸门：
 *       downloading → "model is currently downloading" 等）；</li>
 *   <li><b>chat / vlm / asr</b> 走 {@code findModelVisible} 直取（无状态闸门）。</li>
 * </ul>
 *
 * <p>错误形态：调用方（DebugModel）把异常消息写进 {@code data.error}（HTTP 200）。
 * 因此本类抛出的 {@link RuntimeException} 的 {@code getMessage()} 即对外文案
 * （错误文案是契约）；底层部件抛 {@link BizException} 时在此拆包取其 message
 * （BizException 的 getMessage 带 "error code: ..." 前缀，不能直接当对外文案用）。</p>
 */
@Component
public class ModelRuntimeAssembler implements ModelRuntime {

    private static final Logger log = LoggerFactory.getLogger(ModelRuntimeAssembler.class);

    private final ModelCatalogPort modelCatalog;
    private final CryptoService cryptoService;
    private final ObjectProvider<OllamaService> ollamaService;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final SsrfGuard ssrfGuard;

    public ModelRuntimeAssembler(ModelCatalogPort modelCatalog,
                               CryptoService cryptoService, ObjectProvider<OllamaService> ollamaService,
                               ConcurrencyGovernor concurrencyGovernor, SsrfGuard ssrfGuard) {
        this.modelCatalog = modelCatalog;
        this.cryptoService = cryptoService;
        this.ollamaService = ollamaService;
        this.concurrencyGovernor = concurrencyGovernor;
        this.ssrfGuard = ssrfGuard;
    }

    /** 直取模型行（无状态闸门）→ chat 客户端装配。 */
    @Override
    public LlmChatClient getChatModel(String modelId) {
        ModelRowView model = getModelDirect(modelId);
        log.info("Getting chat model: {}, source: {}", model.name(), model.source());
        String[] creds = modelCredentials(model.parameters());
        try {
            // langfuse generation 装饰（未启用时原样返回，零成本）
            return LangfuseChatClient.wrap(
                    LlmChatClients.create(ModelRuntimeConfigs.chatConfig(model, creds[0], creds[1]),
                            ollamaService.getIfAvailable(), concurrencyGovernor));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** 带状态闸门取行 → embedder 工厂装配。 */
    @Override
    public Embedder getEmbeddingModel(String modelId) {
        ModelRowView model = getModelGated(modelId);
        log.info("Getting embedding model: {}, source: {}", model.name(), model.source());
        String[] creds = modelCredentials(model.parameters());
        try {
            // pooler 只服务批量向量化；debug 只走单文本 embed，传 null
            // langfuse generation 装饰
            return LangfuseEmbedder.wrap(
                    EmbedderFactory.newEmbedder(
                            ModelRuntimeConfigs.embedderConfig(model, creds[0], creds[1]),
                            null, ollamaService.getIfAvailable(), concurrencyGovernor));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** 带状态闸门取行 → reranker 工厂装配。 */
    @Override
    public Reranker getRerankModel(String modelId) {
        ModelRowView model = getModelGated(modelId);
        log.info("Getting rerank model: {}, source: {}", model.name(), model.source());
        String[] creds = modelCredentials(model.parameters());
        try {
            // langfuse generation 装饰
            return LangfuseReranker.wrap(
                    RerankerFactory.newReranker(
                            ModelRuntimeConfigs.rerankerConfig(model, creds[0], creds[1])));
        } catch (BizException e) {
            throw new RuntimeException(e.appError().message());
        }
    }

    /** 直取（无状态闸门）。调用方再 configFromModel + predict。 */
    @Override
    public ModelRowView getVlmModel(String modelId) {
        ModelRowView model = getModelDirect(modelId);
        log.info("Getting VLM model: {}, source: {}", model.name(), model.source());
        return model;
    }

    /**
     * VLM 客户端配置：构造 + 非 ollama 的基址 SSRF 校验
     * （validateVLMBaseURL；ollama 不校验基址）。
     *
     * <p>失败抛 {@link RuntimeException}，message 即对外错误文案——调用方
     * （模型调试端点、agent 引擎装配）直接写进 {@code data.error}。</p>
     */
    @Override
    public VlmConfig vlmConfigFor(ModelRowView model) {
        VlmConfig config = ModelRuntimeConfigs.vlmConfig(model);
        if (!config.isOllama()) {
            validateVlmBaseUrl(config.baseUrl());
        }
        return config;
    }

    /** 直取（无状态闸门）。调用方再组 AsrConfig + transcribe。 */
    @Override
    public ModelRowView getAsrModel(String modelId) {
        ModelRowView model = getModelDirect(modelId);
        log.info("Getting ASR model: {}, source: {}", model.name(), model.source());
        return model;
    }

    /**
     * 构造期校验（validateVLMBaseURL）：SSRF 失败文案
     * "base URL SSRF check failed: ..."。
     */
    @Override
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

    /** 租户可见模型行（含 is_builtin；无状态闸门）。 */
    private ModelRowView getModelDirect(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            throw new RuntimeException("model ID cannot be empty");
        }
        long tid = TenantContext.currentTenantId() == null ? 0 : TenantContext.currentTenantId();
        ModelRowView model = modelCatalog.findModelVisible(tid, modelId);
        if (model == null) {
            throw new RuntimeException("model not found");
        }
        return model;
    }

    /**
     * 带状态闸门取行，错误文案拆包为对外原文。
     *
     * <p>{@code findModelOrEmpty} 只把"行不存在"收成空（别的失败照抛），
     * 状态闸门的 BizException 因此原样冒出来，在此拆成对外文案。</p>
     */
    private ModelRowView getModelGated(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            throw new RuntimeException("model ID cannot be empty");
        }
        try {
            return modelCatalog.findModelOrEmpty(modelId)
                    .orElseThrow(() -> new RuntimeException("model not found"));
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
