package com.ragagent.knowledge.service.image;

import com.ragagent.platform.knowledge.ImageRefRewriter;
import com.ragagent.platform.security.SsrfGuard;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 网页正文里的外链图片：导入时下载下来，交给 {@link ExtractedImageStore} 落盘并重写引用。
 *
 * <p>HTML 转 markdown 后图片只是 {@code ![](https://...)} 文本，docreader 的 imageRefs
 * 不含它们；不下载的话前端直接加载外链，遇到防盗链/跨域就是破图。</p>
 *
 * <p>约束：单篇最多 {@value #MAX_IMAGES} 张、单图 ≤5MB、5s 超时；带页面 URL 作 Referer
 * （多数 CDN 靠它放行）；SSRF 校验先行；单张失败只跳过该图，原链接保留。</p>
 */
@Component
public class RemoteImageRefs {

    private static final Logger log = LoggerFactory.getLogger(RemoteImageRefs.class);

    static final int MAX_IMAGES = 30;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Pattern MD_IMAGE =
            Pattern.compile("!\\[[^\\]]*\\]\\(\\s*<?(https?://[^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");

    private final SsrfGuard ssrfGuard;

    public RemoteImageRefs(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    /** 扫描 markdown 的 http(s) 图片引用，下载成功的转成 RefRow；失败/超限跳过。 */
    public List<ImageRefRewriter.RefRow> collect(String markdown, String pageUrl) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        Matcher m = MD_IMAGE.matcher(markdown);
        while (m.find() && urls.size() < MAX_IMAGES) {
            urls.add(m.group(1));
        }
        if (urls.isEmpty()) {
            return List.of();
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
        List<ImageRefRewriter.RefRow> rows = new ArrayList<>();
        for (String url : urls) {
            try {
                ssrfGuard.validateURLForSSRF(url);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .GET();
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    builder.header("Referer", pageUrl);
                }
                HttpResponse<byte[]> resp = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() / 100 != 2) {
                    log.debug("skip remote image {}: HTTP {}", url, resp.statusCode());
                    continue;
                }
                byte[] data = resp.body();
                if (data.length == 0 || data.length > MAX_BYTES) {
                    log.debug("skip remote image {}: size {}", url, data.length);
                    continue;
                }
                String mime = resp.headers().firstValue("Content-Type").orElse("");
                rows.add(new ImageRefRewriter.RefRow(url, fileNameOf(url, mime), mime, data));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return rows;
            } catch (Exception e) {
                log.debug("skip remote image {}", url, e);
            }
        }
        return rows;
    }

    /** 从 URL 取文件名；无扩展名时按 Content-Type 补一个，便于落盘与后续 MIME 判定。 */
    static String fileNameOf(String url, String contentType) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isEmpty()) {
            name = "image";
        }
        if (name.contains(".")) {
            return name;
        }
        String ext = switch (mimeOf(contentType)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "png";
        };
        return name + "." + ext;
    }

    private static String mimeOf(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }
}
