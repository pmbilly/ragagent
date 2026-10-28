package com.ragagent.knowledge.service.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 网页导入取 HTML {@code <title>} 作为文档标题。 */
class HtmlTitlesTest {

    private static byte[] html(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("取 <title>，做实体解码与空白归一")
    void extractsAndNormalizes() {
        String body = "<html><head><title>\n  昊铂 S600 &amp; 埃安\n 卖点 </title></head><body></body></html>";
        assertThat(HtmlTitles.extract(html(body), "text/html; charset=utf-8"))
                .isEqualTo("昊铂 S600 & 埃安 卖点");
    }

    @Test
    @DisplayName("title 属性/大小写不敏感；无 title 返回 null")
    void handlesAttributesAndMissing() {
        assertThat(HtmlTitles.extract(html("<TITLE lang=\"zh\">车型</TITLE>"), "text/html"))
                .isEqualTo("车型");
        assertThat(HtmlTitles.extract(html("<html><body>没有标题</body></html>"), "text/html"))
                .isNull();
        assertThat(HtmlTitles.extract(html("<title>   </title>"), "text/html"))
                .isNull();
    }

    @Test
    @DisplayName("超长 title 截断到上限")
    void truncatesLongTitle() {
        String longTitle = "a".repeat(HtmlTitles.MAX_TITLE_LENGTH + 50);
        String got = HtmlTitles.extract(html("<title>" + longTitle + "</title>"), "text/html");
        assertThat(got).hasSize(HtmlTitles.MAX_TITLE_LENGTH);
    }

    @Test
    @DisplayName("空 body 返回 null")
    void emptyBody() {
        assertThat(HtmlTitles.extract(new byte[0], "text/html")).isNull();
        assertThat(HtmlTitles.extract(null, "text/html")).isNull();
    }
}
