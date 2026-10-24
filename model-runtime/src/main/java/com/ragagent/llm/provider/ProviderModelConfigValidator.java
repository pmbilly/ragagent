package com.ragagent.llm.provider;

import com.ragagent.contract.model.ModelConfigDraft;
import com.ragagent.contract.model.ModelConfigValidator;
import org.springframework.stereotype.Component;

/**
 * 用厂商自带的 {@link Provider#validateConfig(Provider.Config)} 做保存期结构校验。
 *
 * <p>只做字段级判断（key / model name / base URL），不发起任何网络请求。
 * provider 为空或不在 {@link ProviderName} 内（本地 Ollama、自定义 OpenAI 兼容端点）
 * 时没有厂商规则，直接放行。</p>
 */
@Component
public class ProviderModelConfigValidator implements ModelConfigValidator {

    @Override
    public void validate(ModelConfigDraft draft) {
        if (draft == null) {
            return;
        }
        ProviderName name = ProviderName.fromValue(draft.provider());
        if (name == null) {
            return;
        }
        ProviderRegistry.get(name).ifPresent(provider -> provider.validateConfig(new Config(
                name, draft.baseUrl(), draft.apiKey(), draft.modelName(), "", null)));
    }
}
