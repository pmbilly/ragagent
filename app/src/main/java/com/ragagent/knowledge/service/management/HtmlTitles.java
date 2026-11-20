package com.ragagent.knowledge.service.management;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页导入的标题提取：取 HTML 的 {@code <title>} 作为文档标题。
 *
 * <p>只在 html 类型上调用；找不到或为空时由调用方回落到文件名。标题做实体解码与
 * 空白归一，并截断到 {@value #MAX_TITLE_LENGTH} 字符，避免超长 title 污染列表。</p>
 */
final class HtmlTitles {

    static final int MAX_TITLE_LENGTH = 200;

    /** 只在头部这段字节里找 title，避免为取标题解码整个大文件。 */
    private static final int SCAN_LIMIT_BYTES = 512 * 1024;

    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=([a-z0-9_-]+)");

    private HtmlTitles() {
    }

    /** 返回页面标题（已归一/截断）；没有 {@code <title>} 或为空返回 null。 */
    static String extract(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return null;
        }
        int limit = Math.min(body.length, SCAN_LIMIT_BYTES);
        String text = new String(body, 0, limit, charsetOf(contentType));
        Matcher m = TITLE.matcher(text);
        if (!m.find()) {
            return null;
        }
        String title = decodeEntities(m.group(1)).replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) {
            return null;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    /** content-type 里的 charset 优先，未知/缺失按 UTF-8。 */
    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            Matcher m = CHARSET.matcher(contentType);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException ignored) {
                    // 未知 charset 回落 UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** 常见实体解码；&amp; 放最后，避免二次解码。 */
    private static String decodeEntities(String s) {
        return s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
