package com.ragagent.common.web;

/**
 * 五字符 HTML 转义（{@code & ' < > "} → {@code &amp; &#39; &lt; &gt; &#34;}）的单一实现。
 *
 * <p>单趟逐字符替换，不二次转义；{@code null} → 空串。</p>
 */
public final class HtmlText {

    private HtmlText() {
    }

    public static String escape(String s) {
        String v = s == null ? "" : s;
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '\'' -> sb.append("&#39;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&#34;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
