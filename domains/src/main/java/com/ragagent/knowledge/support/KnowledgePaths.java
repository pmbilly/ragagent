package com.ragagent.knowledge.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * knowledge 的**文件名/下载名规则**（由 {@code service.KnowledgeService} 抽出，
 * 与 {@code datasource.MapperKnowledgeBridge} 的同名私有实现**逐字比对等价**后合并为单一实现
 * ——§9.2"同一规则只允许一份实现"）。
 *
 * <p>方向：support ← 任意面（本类不依赖任何 service）。</p>
 */
public final class KnowledgePaths {

    private KnowledgePaths() {
    }

    public static String ensureManualFileName(String title) {
        String base = title == null || title.isBlank() ? "manual" : title.trim();
        return base.endsWith(".md") ? base : base + ".md";
    }

    public static String extractFileNameFromUrl(String url) {
        if (url == null) {
            return "download";
        }
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? "download" : name;
    }

/**
 * * \ → /、分段 trim、去尾部 ". "、跳过空/./.. 段、单段 ≤128 字节、深度 ≤16、总长 ≤1024。
 */
public static String normalizeKnowledgeFolderPath(String raw) {
    if (raw == null || raw.isEmpty()) {
        return "";
    }
    raw = raw.replace('\\', '/');
    List<String> segments = new ArrayList<>(8);
    for (String segment : raw.split("/", -1)) {
        segment = segment.trim();
        segment = stripTrailing(segment, ". ");
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
            continue;
        }
        if (segment.getBytes(StandardCharsets.UTF_8).length > 128) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            int cut = 128;
            while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
                cut--; // 回退到 rune 起点
            }
            segment = new String(bytes, 0, cut, StandardCharsets.UTF_8).trim();
        }
        if (segment.isEmpty()) {
            continue;
        }
        segments.add(segment);
        if (segments.size() >= 16) {
            break;
        }
    }
    String path = String.join("/", segments);
    while (path.getBytes(StandardCharsets.UTF_8).length > 1024 && !segments.isEmpty()) {
        segments = segments.subList(0, segments.size() - 1);
        path = String.join("/", segments);
    }
    return path;
}

private static String stripTrailing(String s, String cutset) {
    while (!s.isEmpty() && cutset.indexOf(s.charAt(s.length() - 1)) >= 0) {
        s = s.substring(0, s.length() - 1);
    }
    return s;
}
}
