package com.ragagent.embedding.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * WeKnoraCloud 请求签名（md5 摘要 + nonce + rfc3986 编码的组合签名串）。
 *
 * <p><b>本类是全项目的第二份副本</b>（第一份在 {@code llm.chat.ProviderAdapters}，
 * 包内可见复用不了）——embedding 与 rerank 两个包共用这一份。</p>
 */
public final class WeknoraCloudSign {

    private static final int NONCE_LENGTH = 16;
    private static final String NONCE_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private WeknoraCloudSign() {
    }

    /** 签名入口：apiKey 槽位由 AppSecret 承载。 */
    public static Map<String, String> sign(String appID, String apiKey, String requestID,
                                           String bodyJSON) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = generateNonce(NONCE_LENGTH);

        String bodyForHash = bodyJSON == null || bodyJSON.isEmpty() ? "{}" : bodyJSON;
        String bodyMd5 = md5Hex(bodyForHash);

        // 参数按字母序排列后 "&" 拼接（TreeMap）
        Map<String, String> params = new TreeMap<>();
        params.put("x-appid", appID);
        params.put("x-api-key", apiKey);
        params.put("x-request-id", requestID);
        params.put("x-timestamp", timestamp);
        params.put("x-nonce", nonce);
        params.put("body", bodyMd5);

        List<String> parts = new ArrayList<>(params.size());
        params.forEach((k, v) -> parts.add(rfc3986Encode(k) + "=" + rfc3986Encode(v)));
        String signature = md5Hex(String.join("&", parts));

        return Map.of(
                "X-APPID", appID,
                "X-API-Key", apiKey,
                "X-Request-ID", requestID,
                "X-Timestamp", timestamp,
                "X-Nonce", nonce,
                "X-Signature", signature);
    }

    static String generateNonce(int length) {
        StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            b.append(NONCE_CHARS.charAt(NONCE_RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return b.toString();
    }

    static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RFC3986 百分号编码：保留 unreserved 字符，其余 %XX（大写十六进制）。 */
    static String rfc3986Encode(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xFF));
            }
        }
        return out.toString();
    }
}
