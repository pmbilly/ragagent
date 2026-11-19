package com.ragagent.im.channel.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 飞书 {@code tenant_access_token} 的取值与缓存，以及 API 地址拼接。
 *
 * <p><b>单飞</b>：同一时刻只发一次刷新请求；其余线程在锁内读到新值。缓存留 5 分钟余量，
 * 避免恰好在过期边界发出请求。</p>
 *
 * <p>从 {@code FeishuAdapter} 抽出，目的是让发消息 / 卡片流 / 图片上传三个协作者直接注入它，
 * 而不是持有整个 Adapter。token 是运行态状态，<b>必须单实例</b>——若各协作者各持一份，
 * 就等于三份缓存、三次刷新。</p>
 */
final class FeishuTokenProvider {

    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(10);
    private static final int EXPIRY_MARGIN_SECONDS = 300;

    private final String appId;
    private final String appSecret;
    private final String apiBaseUrl;
    private final HttpClient http;
    private final Object lock = new Object();

    private String cache = "";
    private Instant expiresAt = Instant.EPOCH;

    FeishuTokenProvider(String appId, String appSecret, String apiBaseUrl, HttpClient http) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiBaseUrl = apiBaseUrl;
        this.http = http;
    }

    /** 拼 API 地址（{@code apiBaseUrl + path}）。 */
    String api(String path) {
        return apiBaseUrl + path;
    }

    String getTenantAccessToken() throws Exception {
        synchronized (lock) {
            if (!cache.isEmpty() && Instant.now().isBefore(expiresAt)) {
                return cache;
            }
        }
        Map<String, String> payload = Map.of("app_id", appId, "app_secret", appSecret);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(api("/open-apis/auth/v3/tenant_access_token/internal")))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(TOKEN_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(
                        FeishuAdapter.MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        JsonNode result = FeishuAdapter.readTree(response.body());
        int code = result.path("code").asInt(0);
        if (code != 0) {
            throw new IllegalStateException("get token error: code=" + code + " msg="
                    + result.path("msg").asText(""));
        }
        String token = result.path("tenant_access_token").asText("");
        long expireSeconds = result.path("expire").asLong(0);
        synchronized (lock) {
            cache = token;
            long ttl = expireSeconds > EXPIRY_MARGIN_SECONDS
                    ? expireSeconds - EXPIRY_MARGIN_SECONDS : 0;
            expiresAt = Instant.now().plusSeconds(ttl);
        }
        return token;
    }
}
