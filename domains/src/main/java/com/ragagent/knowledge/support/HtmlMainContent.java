package com.ragagent.knowledge.support;

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * HTML 导入的正文裁剪：保守删除样板容器，保留主体与全部表格。
 *
 * <p>不做 Readability 式“正文识别”——表格密集的配置页会被整段判成非正文，
 * 实测 8 张表只剩 1 张。这里只删明确是样板的标签（脚本/样式/导航/页头/页脚/侧栏），
 * 再优先取 {@code main} / {@code article} / {@code body} 作为根。</p>
 *
 * <p>裁剪后纯文本过短（SPA 空 body）时回退原文，避免导入成空文档。</p>
 */
public final class HtmlMainContent {

    /** 裁剪后纯文本低于此长度就认为不可用，回退原文。 */
    static final int MIN_TEXT_LENGTH = 200;

    private static final String BOILERPLATE =
            "script, style, noscript, iframe, svg, nav, header, footer, aside, "
                    + "[role=navigation], [role=banner], [role=contentinfo]";

    private HtmlMainContent() {
    }

    /** 输入 HTML 字节，返回裁剪后的片段；无法裁剪时原样返回。 */
    public static byte[] extract(byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }
        try {
            Document doc = Jsoup.parse(new String(body, StandardCharsets.UTF_8));
            doc.select(BOILERPLATE).remove();
            Element root = doc.selectFirst("main");
            if (root == null) {
                root = doc.selectFirst("article");
            }
            if (root == null) {
                root = doc.body();
            }
            if (root == null) {
                return body;
            }
            String html = root.html();
            if (Jsoup.parse(html).text().length() < MIN_TEXT_LENGTH) {
                return body;
            }
            return html.getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return body;
        }
    }
}
