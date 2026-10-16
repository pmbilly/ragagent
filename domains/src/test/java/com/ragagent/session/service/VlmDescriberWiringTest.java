package com.ragagent.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.model.VlmPredictor;

/**
 * VLM 描述器装配的验收（R3a 后口径）：本类只剩"取行 → 转交预测器"，
 * 故只断言委托与失败传播——装配链（配置/SSRF/观测/闸门）与 predict 的 HTTP 形状
 * 分别由 {@code model-runtime} 的 {@code ModelRuntimeAssemblerVlmTest} / {@code VlmClientTest} 覆盖。
 */
class VlmDescriberWiringTest {

    private final ModelRuntime runtimeFactory = mock(ModelRuntime.class);

    private VlmDescriberWiring wiring() {
        return new VlmDescriberWiring(runtimeFactory);
    }

    private static ModelRowView vlmRow() {
        ModelParameters p = new ModelParameters();
        p.setBaseUrl("https://vision.test/v1");
        p.setExtraConfig(Map.of());
        return new ModelRowView("vlm-1", "vlm-test", "", "remote", "active", false, p);
    }

    @Test
    @DisplayName("逐张图片经预测器描述（委托不丢参）")
    void describeDelegatesToPredictor() {
        ModelRowView row = vlmRow();
        when(runtimeFactory.getVlmModel("vlm-1")).thenReturn(row);
        List<String> prompts = new ArrayList<>();
        when(runtimeFactory.vlmPredictorFor(row)).thenReturn((image, prompt) -> {
            prompts.add(prompt);
            return "a chart";
        });

        var describer = wiring().create("vlm-1");

        assertThat(describer.describe(new byte[] {1, 2, 3}, "describe this")).isEqualTo("a chart");
        assertThat(prompts).containsExactly("describe this");
        verify(runtimeFactory).getVlmModel("vlm-1");
    }

    @Test
    @DisplayName("取模型失败：原样抛（调用方记警告后走无描述回落）")
    void assemblyFailurePropagates() {
        when(runtimeFactory.getVlmModel("vlm-missing"))
                .thenThrow(new RuntimeException("model not found"));

        assertThatThrownBy(() -> wiring().create("vlm-missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("model not found");
    }

    @Test
    @DisplayName("predict 失败：文案原样上抛")
    void predictFailureSurfacesGoMessage() {
        ModelRowView row = vlmRow();
        when(runtimeFactory.getVlmModel("vlm-1")).thenReturn(row);
        VlmPredictor failing = (image, prompt) -> {
            throw new RuntimeException("OpenAI VLM request: connection refused");
        };
        when(runtimeFactory.vlmPredictorFor(row)).thenReturn(failing);

        var describer = wiring().create("vlm-1");

        assertThatThrownBy(() -> describer.describe(new byte[] {1}, "p"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OpenAI VLM request: connection refused");
    }
}
