package com.ragagent.platform.http;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.platform.storage.config.UploadLimits;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * URL → 字节的**统一抓取**：知识库 URL 导入与数据源同步共用一份口径。
 *
 * <p>HTTP 口径（自 {@code KnowledgeService} 的私有实现原样搬入）：跟 NORMAL 重定向、
 * 连接超时 30s、读超时 2min、非 2xx 报 {@code BizException}；响应体超过上传上限
 * （{@link UploadLimits}）报同一句文案——URL 导入的产物就是"一份上传的文件"，
 * 上限共用一条政策。</p>
 *
 * <p><b>SSRF 校验由调用方按需注入</b>：HTTP 入口在 controller 校验（那里要回 400），
 * 后台同步/队列没有 controller ⇒ 自己传 {@code ssrfGuard::validateURLForSSRF}。</p>
 */
public final class UrlBytesFetcher {

    private UrlBytesFetcher() {
    }

    /** 抓取结果：响应体 + 响应头 {@code Content-Type}（可能为空串）。 */
    public record FetchedUrl(byte[] body, String contentType) {
    }

    /** 抓取并做大小上限校验（不额外做 SSRF 校验——调用方已在更外层校验时用）。 */
    public static byte[] fetch(String url) {
        return fetchWithMeta(url, null).body();
    }

    /**
     * @param ssrfCheck 非 null 时在发起请求前执行（通常传
     *                  {@code ssrfGuard::validateURLForSSRF}）；它抛出的异常原样冒泡，
     *                  由调用方归类（HTTP 400 / 同步条目失败）
     */
    public static byte[] fetch(String url, Consumer<String> ssrfCheck) {
        return fetchWithMeta(url, ssrfCheck).body();
    }

    /**
     * 同 {@link #fetch(String, Consumer)}，但保留响应头 Content-Type——
     * 无扩展名的网页 URL 靠它判定 html/pdf/image，不再把类型猜成空串。
     */
    public static FetchedUrl fetchWithMeta(String url, Consumer<String> ssrfCheck) {
        if (ssrfCheck != null) {
            ssrfCheck.accept(url);
        }
        byte[] body;
        String contentType;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new BizException(AppError.badRequest("failed to fetch URL: HTTP " + resp.statusCode()));
            }
            body = resp.body();
            contentType = resp.headers().firstValue("Content-Type").orElse("");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(AppError.badRequest("failed to fetch URL: " + e.getMessage()));
        }
        if (body.length > UploadLimits.maxFileSizeBytes()) {
            throw new BizException(AppError.badRequest(
                    String.format("文件大小不能超过%dMB", UploadLimits.maxFileSizeMb())));
        }
        return new FetchedUrl(body, contentType);
    }
}
