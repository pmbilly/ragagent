package com.ragagent.wiki.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 请求体绑定的 400 文案钉子（非对象 body / EOF）。 */
class WikiRequestSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nonObjectBodyYieldsJsonTypeName() {
        assertThatThrownBy(() -> WikiRequestSupport.readJsonBody(MAPPER, "[1,2]"))
                .isInstanceOf(WikiPageController.RawJsonError.class)
                .hasMessage("Invalid request body: expected JSON object, got array");
        assertThatThrownBy(() -> WikiRequestSupport.readJsonBody(MAPPER, "\"s\""))
                .hasMessage("Invalid request body: expected JSON object, got string");
    }

    @Test
    void emptyBodyKeepsEofMessage() {
        assertThatThrownBy(() -> WikiRequestSupport.readJsonBody(MAPPER, " "))
                .hasMessage("Invalid request body: EOF");
    }
}
