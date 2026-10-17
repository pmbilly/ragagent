package com.ragagent.modelruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.model.VlmPredictor;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.platform.security.CryptoService;
import com.ragagent.platform.security.SsrfGuard;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ModelRuntime#vlmPredictorFor} 的装配语义（原 domains 侧 VlmDescriberWiringTest 的
 * 装配断言随 R3a 迁到这里——那些行为本就属于装配器）。
 *
 * <p>predict 的 HTTP 形状与错误文案由 {@code VlmClientTest} 覆盖，此处只测装配：
 * 取行失败文案、SSRF 校验与 ollama 豁免、predictor 可用性。</p>
 */
class ModelRuntimeAssemblerVlmTest {

    private final ModelCatalogPort catalog = mock(ModelCatalogPort.class);
    private final CryptoService crypto = mock(CryptoService.class);
    private final ConcurrencyGovernor governor = new ConcurrencyGovernor();
    private final SsrfGuard ssrfGuard = mock(SsrfGuard.class);

    private final ModelRuntimeAssembler assembler =
            new ModelRuntimeAssembler(catalog, crypto, null, governor, ssrfGuard);

    private static ModelRowView vlmRow(String source, String baseUrl) {
        ModelParameters p = new ModelParameters();
        p.setBaseUrl(baseUrl);
        p.setExtraConfig(Map.of());
        return new ModelRowView("vlm-1", "vlm-test", "", source, "active", false, p);
    }

    @Test
    @DisplayName("取行失败：model not found（对外文案）")
    void modelMissingFailsWithGoText() {
        when(catalog.findModelVisible(anyLong(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> assembler.getVlmModel("vlm-missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("model not found");
    }

    @Test
    @DisplayName("非 ollama 基址走 SSRF 校验，失败文案带 base URL SSRF check failed 前缀")
    void ssrfFailurePropagatesWithGoPrefix() {
        doThrow(new RuntimeException("restricted hostname: localhost"))
                .when(ssrfGuard).validateURLForSSRF(anyString());

        assertThatThrownBy(() -> assembler.vlmPredictorFor(vlmRow("remote", "https://vision.test/v1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("base URL SSRF check failed: restricted hostname: localhost");
    }

    @Test
    @DisplayName("ollama 分支跳过基址校验，predictor 可用")
    void ollamaSkipsBaseUrlValidation() {
        // 本机回环基址：非 ollama 界面会被 SSRF 拒绝；ollama 分支不校验
        VlmPredictor predictor = assembler.vlmPredictorFor(vlmRow("local", "http://127.0.0.1:11434"));

        assertThat(predictor).isNotNull();
        verify(ssrfGuard, never()).validateURLForSSRF(anyString());
    }
}
