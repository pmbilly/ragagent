package com.ragagent.knowledge.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 网页导入只保留主体内容：导航/页头/页脚/相关推荐不进正文。 */
class HtmlMainContentTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("去掉 nav/header/footer，只留 article 正文")
    void stripsBoilerplate() {
        String html = "<html><head><title>车型页</title></head><body>"
                + "<header>网站页头 导航菜单 首页 车型 经销商</header>"
                + "<nav>首页 | 车型 | 服务 | 关于我们</nav>"
                + "<main><article><h1>AION V 配置</h1>"
                + "<p>" + "正文段落内容。".repeat(40) + "</p></article></main>"
                + "<aside>相关推荐：其他车型</aside>"
                + "<footer>版权所有 隐私政策 联系我们</footer></body></html>";

        String out = new String(HtmlMainContent.extract(bytes(html)), StandardCharsets.UTF_8);

        assertThat(out).contains("AION V 配置");
        assertThat(out).doesNotContain("隐私政策");
        assertThat(out).doesNotContain("相关推荐");
    }

    @Test
    @DisplayName("表格密集页：所有 table 都保留（不做正文识别裁剪）")
    void keepsAllTables() {
        StringBuilder sb = new StringBuilder("<html><body><nav>导航</nav><main>");
        for (int i = 0; i < 8; i++) {
            sb.append("<table><tr><td>配置").append(i).append("</td><td>●</td></tr></table>");
        }
        sb.append("<p>").append("正文内容。".repeat(50)).append("</p></main></body></html>");
        String out = new String(HtmlMainContent.extract(bytes(sb.toString())),
                StandardCharsets.UTF_8);

        assertThat(out).contains("配置0").contains("配置7");
        assertThat(out.split("<table", -1).length - 1).isEqualTo(8);
    }

    @Test
    @DisplayName("正文过短（SPA 空 body）回退原文，不导入空文档")
    void shortContentFallsBackToOriginal() {
        byte[] html = bytes("<html><body><div id=app></div></body></html>");
        assertThat(HtmlMainContent.extract(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("空输入原样返回")
    void emptyInput() {
        assertThat(HtmlMainContent.extract(new byte[0])).isEmpty();
        assertThat(HtmlMainContent.extract(null)).isNull();
    }
}
