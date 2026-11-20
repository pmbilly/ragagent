package com.ragagent.platform.text;

/**
 * Unicode 文本原语。
 *
 * <p>{@link #trim(String)} 按完整空白集合去两端：{@link Character#isWhitespace}
 * 覆盖 {@code \t \n \v \f \r}、U+0085 与大部分 Zs；{@link Character#isSpaceChar}
 * 补上 U+00A0 / U+2007 / U+202F 等“非断行空格”。{@link String#trim()} 只去
 * {@code <= U+0020}，{@link String#strip()} 也不含 U+00A0——两者都不够。</p>
 */
public final class UnicodeText {

    private UnicodeText() {
    }

    /** 去两端 Unicode 空白；null 归一为空串。 */
    public static String trim(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = s.length();
        while (start < end && isUnicodeWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && isUnicodeWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c)
                || c == '\u0085' || c == '\u00A0';
    }
}
