package com.ragagent.auth.controller.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthFilterApiDocsTest {

    @Test
    void apiDocsPathsAreWhitelisted() {
        assertThat(AuthFilter.isApiDocsPath("/v3/api-docs")).isTrue();
        assertThat(AuthFilter.isApiDocsPath("/v3/api-docs/swagger-config")).isTrue();
        assertThat(AuthFilter.isApiDocsPath("/scalar")).isTrue();
        assertThat(AuthFilter.isApiDocsPath("/scalar/")).isTrue();
    }

    @Test
    void businessPathsAreNotWhitelisted() {
        assertThat(AuthFilter.isApiDocsPath("/api/v1/sessions")).isFalse();
        assertThat(AuthFilter.isApiDocsPath("/v3/api-docs-evil")).isFalse();
        assertThat(AuthFilter.isApiDocsPath("/swagger-ui.html")).isFalse();
        assertThat(AuthFilter.isApiDocsPath("/swagger-ui/index.html")).isFalse();
        assertThat(AuthFilter.isApiDocsPath(null)).isFalse();
    }
}
