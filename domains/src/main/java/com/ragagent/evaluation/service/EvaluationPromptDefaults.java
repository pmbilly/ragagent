package com.ragagent.evaluation.service;

import com.ragagent.common.prompt.PromptSource;
import com.ragagent.common.prompt.PromptTemplates;

/**
 * 评估参数的生效值（golden ev-post.json 钉住的响应体内容）。
 *
 * <p>前四个值**不再内嵌字面量**，而是按模板 id 从 {@code prompts/*.yaml} 取
 * （单一字节源；此前是 Java 常量与 vendored 模板逐字双份，改一处漏一处就出现两条口径）。
 * 模板改名/缺失会退化为空串，由 {@code EvaluationPromptDefaultsTest} 守门。</p>
 *
 * <p>后两个值是**评估专用**语义，仓库内没有对应模板，故保留为常量：
 * 无命中前缀是摘要模型的调用约定；兜底句与 {@code fallback.yaml} 里的用户可见文案不同口径。</p>
 */
public final class EvaluationPromptDefaults {

    public static final String SUMMARY_PROMPT =
            PromptTemplates.content(PromptSource.SYSTEM_PROMPT, "default_kb");

    public static final String SUMMARY_CONTEXT_TEMPLATE =
            PromptTemplates.content(PromptSource.CONTEXT_TEMPLATE, "default_context");

    public static final String REWRITE_PROMPT_SYSTEM =
            PromptTemplates.content(PromptSource.REWRITE, "default_rewrite");

    public static final String REWRITE_PROMPT_USER =
            PromptTemplates.user(PromptSource.REWRITE, "default_rewrite");

    /** 评估专用：无命中时给摘要模型的固定前缀（非模板副本）。 */
    public static final String SUMMARY_NO_MATCH_PREFIX = "<think>\n</think>\nNO_MATCH";

    /** 评估专用：兜底回答（与 fallback.yaml 的用户可见文案不同口径，未复用）。 */
    public static final String FALLBACK_RESPONSE = "Sorry, I am unable to answer this question.";

    private EvaluationPromptDefaults() {}
}
