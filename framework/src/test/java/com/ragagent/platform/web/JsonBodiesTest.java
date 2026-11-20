package com.ragagent.platform.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ragagent.contract.error.BizException;
import org.junit.jupiter.api.Test;

/** bindObject 的请求体形态 400 文案钉子（原先 0 金片覆盖）。 */
class JsonBodiesTest {

    @Test
    void nonObjectBodyYieldsJsonTypeName() {
        assertThatThrownBy(() -> JsonBodies.bindObject("[\"a\"]"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).appError().message())
                .isEqualTo("expected JSON object, got array");
        assertThatThrownBy(() -> JsonBodies.bindObject("true"))
                .extracting(e -> ((BizException) e).appError().message())
                .isEqualTo("expected JSON object, got boolean");
    }
}
