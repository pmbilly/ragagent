package com.ragagent.knowledge.service.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 网页导入的 fileType 决议：显式值 > Content-Type > 扩展名 > html。 */
class UrlFileTypeTest {

    @Test
    @DisplayName("显式 fileType 优先于 Content-Type")
    void explicitWins() {
        assertThat(UrlFileTypes.resolve("HTML", "2024", "application/pdf"))
                .isEqualTo("html");
    }

    @Test
    @DisplayName("无扩展名 URL 按 Content-Type 判定（html / pdf），不再落成空串")
    void contentTypeDecidesExtensionlessUrl() {
        assertThat(UrlFileTypes.resolve(null, "2024", "text/html; charset=utf-8"))
                .isEqualTo("html");
        assertThat(UrlFileTypes.resolve(null, "2024", "application/pdf"))
                .isEqualTo("pdf");
        assertThat(UrlFileTypes.resolve(null, "2024", "image/jpeg"))
                .isEqualTo("jpg");
    }

    @Test
    @DisplayName("Content-Type 未知时回落到扩展名，再兜底 html")
    void fallsBackToExtensionThenHtml() {
        assertThat(UrlFileTypes.resolve(null, "report.xlsx", "application/octet-stream"))
                .isEqualTo("xlsx");
        assertThat(UrlFileTypes.resolve(null, "2024", "application/octet-stream"))
                .isEqualTo("html");
        assertThat(UrlFileTypes.resolve(null, "2024", null))
                .isEqualTo("html");
    }
}
