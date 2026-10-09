package com.ragagent.initialization.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ragagent.common.error.BizException;

/** bindJsonObject 的请求体形态 400 文案钉子（原先 0 金片覆盖）。 */
class OllamaBindJsonObjectTest {

    @Test
    void nonObjectBodyYieldsJsonTypeName() {
        assertThatThrownBy(() -> OllamaManageService.bindJsonObject("[\"a\"]"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).appError().message())
                .isEqualTo("expected JSON object, got array");
        assertThatThrownBy(() -> OllamaManageService.bindJsonObject("true"))
                .extracting(e -> ((BizException) e).appError().message())
                .isEqualTo("expected JSON object, got boolean");
    }
}
