package com.ragagent.platform.knowledge;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解析器直出图片引用（内联字节）→ 正文可服务引用 的纯变换：扫描 Markdown 图片目标，
 * 按原始引用命中且有内联字节才替换，落盘结果逐条返回（原始引用 → 可服务引用）。
 *
 * <p>字节落盘由调用方以 {@link Saver} 注入（各域的文件服务不同，本类不做 IO 决策）；
 * 未被正文引用的图片不落盘，命中小图标过滤或落盘失败的引用原样保留。
 * 替换只换目标路径本体，保留空白 / title / 右括号。</p>
 */
public final class ImageRefRewriter {

    private static final Logger log = LoggerFactory.getLogger(ImageRefRewriter.class);

    /** 图标过滤阈值：可解码时两轴均小于该边长，解不开时回退小于字节数下限。 */
    public static final int MIN_IMAGE_DIMENSION = 64;
    public static final int MIN_IMAGE_BYTES = 512;

    /** {@code ![alt](目标 "title")}——分组 1 是引用前缀，分组 2 是路径本体（可能带尖括号）。 */
    private static final Pattern IMAGE_TARGET = Pattern.compile(
            "(!\\[[^\\]]*\\]\\()\\s*(<[^>]*>|[^)]*?)\\s*(?:\"[^\"]*\"\\s*)?\\)");

    private ImageRefRewriter() {
    }

    /** 解析器给出的一条图片：原始引用 + 源文件名 / MIME（可空）+ 内联字节。 */
    public record RefRow(String originalRef, String filename, String mimeType, byte[] data) {
    }

    /** 已落盘的一条图片：原始引用 → 可服务引用。 */
    public record StoredRef(String originalRef, String servingRef, String filename, String mimeType) {
    }

    /** 改写结果：替换后的正文 + 逐条落盘记录（按正文出现序，重复引用各记一条）。 */
    public record Result(String markdown, List<StoredRef> stored) {
    }

    /** 字节落盘接缝：返回可服务引用；抛异常 = 放弃该图（原引用保留）。 */
    @FunctionalInterface
    public interface Saver {
        String save(byte[] data, String fileName);
    }

    /**
     * 改写正文里的图片引用。
     *
     * @param filterIcons 过滤小图标（来源文件本身是图片时传 false——原图不过滤）
     */
    public static Result rewrite(String markdown, List<RefRow> refs, boolean filterIcons, Saver saver) {
        if (refs == null || refs.isEmpty()) {
            return new Result(markdown, List.of());
        }
        Map<String, RefRow> refMap = new LinkedHashMap<>();
        for (RefRow ref : refs) {
            if (ref.originalRef() != null && !ref.originalRef().isEmpty()) {
                refMap.put(ref.originalRef(), ref);
            }
        }
        // 同一张源图（按文件名）只落一次盘，多条引用复用同一可服务引用
        Map<String, String> savedByFilename = new HashMap<>();
        List<StoredRef> stored = new ArrayList<>();

        Matcher m = IMAGE_TARGET.matcher(markdown == null ? "" : markdown);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String rawPath = m.group(2);
            boolean angleWrapped = rawPath.startsWith("<") && rawPath.endsWith(">")
                    && rawPath.length() >= 2;
            String path = angleWrapped ? rawPath.substring(1, rawPath.length() - 1) : rawPath;
            RefRow ref = refMap.get(path);
            if (ref == null || ref.data() == null || ref.data().length == 0) {
                continue; // 无内联字节 → 原样保留
            }
            if (filterIcons && isIconImage(ref.data())) {
                continue;
            }
            String servingRef = ref.filename() == null ? null : savedByFilename.get(ref.filename());
            if (servingRef == null) {
                try {
                    servingRef = saver.save(ref.data(), imageFileName(ref));
                } catch (RuntimeException e) {
                    log.warn("failed to store image {}: {}", path, e.getMessage());
                    continue; // 落盘失败只告警
                }
                if (servingRef == null || servingRef.isEmpty()) {
                    log.warn("storage returned no reference for image {}", path);
                    continue; // 空落盘结果按失败处理，避免把 "null" 写进正文
                }
                if (ref.filename() != null && !ref.filename().isEmpty()) {
                    savedByFilename.put(ref.filename(), servingRef);
                }
            }
            stored.add(new StoredRef(path, servingRef, ref.filename(), ref.mimeType()));
            // 只换路径本体：保留原目标的尾部（空白 / title / 右括号）与尖括号目标包装
            String tail = m.group(0).substring(m.end(2) - m.start());
            String replacement = angleWrapped ? "<" + servingRef + ">" : servingRef;
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + replacement + tail));
        }
        m.appendTail(out);
        return new Result(stored.isEmpty() ? markdown : out.toString(), stored);
    }

    /** 落盘文件名：随机名 + 扩展名（MIME 优先，退回源文件名后缀，再退 .png）。 */
    private static String imageFileName(RefRow ref) {
        String ext = extFromMime(ref.mimeType());
        if (ext.isEmpty()) {
            ext = extOf(ref.filename());
        }
        if (ext.isEmpty()) {
            ext = ".png";
        }
        return UUID.randomUUID() + ext;
    }

    /** 解码尺寸两轴均小于下限视为图标；解码失败回退字节数判定。 */
    public static boolean isIconImage(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                return data.length < MIN_IMAGE_BYTES;
            }
            return image.getWidth() < MIN_IMAGE_DIMENSION && image.getHeight() < MIN_IMAGE_DIMENSION;
        } catch (Exception e) {
            return data.length < MIN_IMAGE_BYTES;
        }
    }

    /** MIME 类型 → 图片扩展名（未知类型返回空串）。 */
    public static String extFromMime(String mime) {
        if (mime == null) {
            return "";
        }
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/svg+xml" -> ".svg";
            default -> "";
        };
    }

    /** 扩展名：小写、带点（".txt"）；无点返回空串。 */
    private static String extOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int idx = lower.lastIndexOf('.');
        return idx < 0 ? "" : lower.substring(idx);
    }
}
