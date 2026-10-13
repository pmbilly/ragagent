package com.ragagent.knowledge.support;

/**
 * knowledge 的**文件名/下载名规则**（B263⑧：由 {@code service.KnowledgeService} 抽出，
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
}
