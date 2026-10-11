package com.ragagent.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.common.prompt.PromptTemplates;

/**
 * 评估生效值的来源守门：四个值必须真从模板取到（模板改名/文件缺失会静默退化为空串，
 * 进而让评估请求带上空提示词——这正是双源时代发现不了的坑）。
 */
class EvaluationPromptDefaultsTest {

    @Test
    @DisplayName("四个模板化默认值非空且与模板同源")
    void defaultsComeFromTemplates() {
        assertFalse(EvaluationPromptDefaults.SUMMARY_PROMPT.isEmpty(),
                "system_prompt.yaml#default_kb.content 取空");
        assertFalse(EvaluationPromptDefaults.SUMMARY_CONTEXT_TEMPLATE.isEmpty(),
                "context_template.yaml#default_context.content 取空");
        assertFalse(EvaluationPromptDefaults.REWRITE_PROMPT_SYSTEM.isEmpty(),
                "rewrite.yaml#default_rewrite.content 取空");
        assertFalse(EvaluationPromptDefaults.REWRITE_PROMPT_USER.isEmpty(),
                "rewrite.yaml#default_rewrite.user 取空");

        assertEquals(PromptTemplates.content("system_prompt.yaml", "default_kb"),
                EvaluationPromptDefaults.SUMMARY_PROMPT);
        assertEquals(PromptTemplates.user("rewrite.yaml", "default_rewrite"),
                EvaluationPromptDefaults.REWRITE_PROMPT_USER);
    }

    @Test
    @DisplayName("评估专用常量保持非空（非模板来源，防止被顺手清掉）")
    void evaluationOnlyConstantsStay() {
        assertFalse(EvaluationPromptDefaults.SUMMARY_NO_MATCH_PREFIX.isEmpty());
        assertFalse(EvaluationPromptDefaults.FALLBACK_RESPONSE.isEmpty());
    }
}
