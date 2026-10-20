package com.ragagent.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 摘要与编码原语：SHA-256 的字节、hex、base64url 三种出口。
 *
 * <p>全仓的 SHA-256 实现收敛到这里；各调用点的差异（hex 截断长度、base64url 是否
 * 无填充）由调用方显式选择，不再各自持有 {@link MessageDigest} 样板。SHA-256 是 JDK
 * 必备算法，缺算法即运行环境损坏，抛 {@link IllegalStateException} 而不是静默降级。</p>
 *
 * <p>hex 一律小写；截断参数按**字符**计（hex）或**字节**计（{@link #hexPrefix}）。</p>
 */
public final class Hashes {

    private Hashes() {
    }

    /** SHA-256 原始字节。 */
    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** UTF-8 输入的 SHA-256 原始字节。 */
    public static byte[] sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 小写十六进制（全量）。 */
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16));
            out.append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }

    /** 小写十六进制的前 {@code bytes} 个字节（每个字节两位）。 */
    public static String hexPrefix(byte[] bytes, int prefixBytes) {
        int n = Math.min(bytes.length, Math.max(0, prefixBytes));
        return hex(java.util.Arrays.copyOf(bytes, n));
    }

    /** SHA-256 → 小写十六进制（全量）。 */
    public static String sha256Hex(byte[] data) {
        return hex(sha256(data));
    }

    /** UTF-8 输入的 SHA-256 → 小写十六进制（全量）。 */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 → 小写十六进制的前 {@code chars} 个字符。 */
    public static String sha256HexPrefix(byte[] data, int chars) {
        String full = sha256Hex(data);
        return full.substring(0, Math.min(full.length(), Math.max(0, chars)));
    }

    /** UTF-8 输入的 SHA-256 → 小写十六进制的前 {@code chars} 个字符。 */
    public static String sha256HexPrefix(String text, int chars) {
        return sha256HexPrefix(text.getBytes(StandardCharsets.UTF_8), chars);
    }

    /** SHA-256 → base64url（无填充）。 */
    public static String sha256Base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(data));
    }

    /** UTF-8 输入的 SHA-256 → base64url（无填充）。 */
    public static String sha256Base64Url(String text) {
        return sha256Base64Url(text.getBytes(StandardCharsets.UTF_8));
    }
}
