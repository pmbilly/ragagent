package com.ragagent.model.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.model.ModelConfigDraft;
import com.ragagent.contract.model.ModelConfigValidator;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.tenant.TenantConfigLookup;
import com.ragagent.model.domain.Model;
import com.ragagent.model.repository.ModelRepository;
import com.ragagent.model.repository.ModelUsageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 保存路径必须经过结构校验；校验失败不得落库。
 */
class ModelServiceConfigValidationTest {

    private final ModelRepository modelRepo = mock(ModelRepository.class);
    private final ModelUsageRepository usageRepo = mock(ModelUsageRepository.class);
    private final TenantConfigLookup tenantConfigLookup = mock(TenantConfigLookup.class);
    private final ModelConfigValidator validator = mock(ModelConfigValidator.class);
    private final ModelService service = new ModelService(
            modelRepo, usageRepo, tenantConfigLookup, validator);

    private static Model model(String id) {
        Model m = new Model();
        m.setId(id);
        m.setName("gpt-4");
        m.setType("KnowledgeQA");
        m.setSource("remote");
        ModelParameters p = new ModelParameters();
        p.setProvider("openai");
        p.setBaseUrl("https://api.openai.com/v1");
        m.setParameters(p);
        return m;
    }

    @Test
    void createPassesDraftAndBlocksInsertWhenInvalid() {
        doThrow(BizException.badRequest("API key is required for OpenAI provider"))
                .when(validator).validate(any());

        assertThrows(BizException.class, () -> service.createModel(model("m1")));

        ArgumentCaptor<ModelConfigDraft> draft = ArgumentCaptor.forClass(ModelConfigDraft.class);
        verify(validator).validate(draft.capture());
        assertEquals("openai", draft.getValue().provider());
        assertEquals("https://api.openai.com/v1", draft.getValue().baseUrl());
        assertEquals("gpt-4", draft.getValue().modelName());
        verify(modelRepo, never()).insert(any());
    }

    @Test
    void updateBlocksWriteWhenInvalid() {
        when(modelRepo.findVisible(0L, "m1")).thenReturn(model("m1"));
        doThrow(BizException.badRequest("bad config")).when(validator).validate(any());

        assertThrows(BizException.class, () -> service.updateModel(model("m1")));

        verify(modelRepo, never()).updateById(any());
    }

    @Test
    void credentialUpdateBlocksWriteWhenInvalid() {
        Model existing = model("m1");
        when(modelRepo.findVisible(0L, "m1")).thenReturn(existing);
        doThrow(BizException.badRequest("bad config")).when(validator).validate(any());

        assertThrows(BizException.class,
                () -> service.updateModelCredentials("m1", "sk-new", null));

        verify(modelRepo, never()).updateById(any());
    }
}
