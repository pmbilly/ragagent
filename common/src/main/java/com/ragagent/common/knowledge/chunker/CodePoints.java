package com.ragagent.common.knowledge.chunker;

/**
 * 码点（Unicode code point）工具。
 * Java 的 {@code char} 是 UTF-16 code unit，增补平面字符（如大部分 CJK 扩展、emoji）
 * 长度一律用 {@link #len(String)}（codePointCount），杜绝 char/char 长度错位。</p>
 */
public final class CodePoints {

    private CodePoints() {
    }

    public static int[] of(String s) {
        return s.codePoints().toArray();
    }

    public static String str(int[] runes, int start, int end) {
        return new String(runes, start, end - start);
    }

    public static int len(String s) {
        return s.codePointCount(0, s.length());
    }

    /** UTF-16 char 偏移 → 码点偏移。 */
    public static int runeIndexAtChar(String s, int charIdx) {
        return s.codePointCount(0, charIdx);
    }

    public static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
