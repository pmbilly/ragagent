package com.ragagent.auth.controller.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthFilterSpringdocTest {

    @Test
    void springdocPathsAreWhitelisted() {
        assertThat(AuthFilter.isSpringdocPath("/v3/api-docs")).isTrue();
        assertThat(AuthFilter.isSpringdocPath("/v3/api-docs/swagger-config")).isTrue();
        assertThat(AuthFilter.isSpringdocPath("/swagger-ui.html")).isTrue();
        assertThat(AuthFilter.isSpringdocPath("/swagger-ui/index.html")).isTrue();
    }

    @Test
    void businessPathsAreNotWhitelisted() {
        assertThat(AuthFilter.isSpringdocPath("/api/v1/sessions")).isFalse();
        assertThat(AuthFilter.isSpringdocPath("/v3/api-docs-evil")).isFalse();
        assertThat(AuthFilter.isSpringdocPath(null)).isFalse();
    }
}
