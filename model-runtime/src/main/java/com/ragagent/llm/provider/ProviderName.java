package com.ragagent.llm.provider;

/**
 * 服务商名枚举（值即 "openai"/"anthropic"/...），既做注册表 key 也做路由判断的返回值。
 * {@link #value()} 的字面量与 DB/线上格式逐字相同（含下划线的 azure_openai）。
 *
 * ⚠️ 枚举的固有限制（调用方需知悉）：未知厂商名（例如 DB 里写入的任意字符串）
 * 无法入枚举，{@link #fromValue(String)} 对未知值返回 null，
 * 调用方必须按"未知厂商"分支处理 null。
 */
public enum ProviderName {

    // ---- 声明序即注册表与展示顺序（OpenAI 起，Azure OpenAI 止） ----
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    ALIYUN("aliyun"),
    ZHIPU("zhipu"),
    OPENROUTER("openrouter"),
    LITELLM("litellm"),
    REQUESTY("requesty"),
    SILICONFLOW("siliconflow"),
    JINA("jina"),
    GENERIC("generic"),
    DEEPSEEK("deepseek"),
    GEMINI("gemini"),
    VOLCENGINE("volcengine"),
    HUNYUAN("hunyuan"),
    MINIMAX("minimax"),
    MIMO("mimo"),
    GPUSTACK("gpustack"),
    MOONSHOT("moonshot"),
    MODELSCOPE("modelscope"),
    QIANFAN("qianfan"),
    QINIU("qiniu"),
    LONGCAT("longcat"),
    LKEAP("lkeap"),
    NVIDIA("nvidia"),
    NOVITA("novita"),
    AZURE_OPEN_AI("azure_openai");

    private final String value;

    ProviderName(String value) {
        this.value = value;
    }

    /** 字符串字面量（注册表 key / DB parameters.provider） */
    public String value() {
        return value;
    }

    /** 字符串 → 枚举；未知或空返回 null（调用方需处理 null） */
    public static ProviderName fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ProviderName p : values()) {
            if (p.value.equals(value)) {
                return p;
            }
        }
        return null;
    }
}
