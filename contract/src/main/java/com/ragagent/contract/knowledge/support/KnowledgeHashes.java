package com.ragagent.contract.knowledge.support;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 内容摘要工具（L1）：知识去重用的 MD5 十六进制。
 *
 * <p>方法体自 {@code knowledge.storage.LocalStorageService#md5Hex} 原样搬入
 * （算法、大小写与异常口径逐字不变）；原入口保留同名委托。</p>
 */
public final class KnowledgeHashes {

    private KnowledgeHashes() {
    }

    /** 内容 MD5（小写十六进制）。 */
    public static String md5Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
