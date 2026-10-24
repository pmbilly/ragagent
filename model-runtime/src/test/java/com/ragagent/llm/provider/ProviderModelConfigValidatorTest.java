package com.ragagent.llm.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.model.ModelConfigDraft;
import org.junit.jupiter.api.Test;

/**
 * 保存期结构校验：厂商规则复用、未知 provider 放行、空输入安全。
 */
class ProviderModelConfigValidatorTest {

    private final ProviderModelConfigValidator validator = new ProviderModelConfigValidator();

    @Test
    void openAiRequiresApiKeyAndModelName() {
        BizException missingKey = assertThrows(BizException.class,
                () -> validator.validate(new ModelConfigDraft("openai", "", "", "gpt-4")));
        assertTrue(missingKey.appError().message().contains("API key"),
                missingKey.appError().message());

        BizException missingModel = assertThrows(BizException.class,
                () -> validator.validate(new ModelConfigDraft("openai", "", "sk-test", "")));
        assertTrue(missingModel.appError().message().contains("model name"),
                missingModel.appError().message());

        assertDoesNotThrow(() -> validator.validate(
                new ModelConfigDraft("openai", "", "sk-test", "gpt-4")));
    }

    @Test
    void genericRequiresBaseUrl() {
        BizException missingBaseUrl = assertThrows(BizException.class,
                () -> validator.validate(new ModelConfigDraft("generic", "", "", "m")));
        assertTrue(missingBaseUrl.appError().message().contains("base URL"),
                missingBaseUrl.appError().message());
    }

    @Test
    void unknownOrLocalProviderHasNoVendorRules() {
        assertDoesNotThrow(() -> validator.validate(new ModelConfigDraft("", "", "", "")));
        assertDoesNotThrow(() -> validator.validate(
                new ModelConfigDraft("ollama", "", "", "qwen2.5:7b")));
        assertDoesNotThrow(() -> validator.validate(null));
    }
}
