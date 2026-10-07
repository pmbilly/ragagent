package com.ragagent.llm.asr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.common.security.SsrfGuard;
import com.ragagent.llm.chat.LlmTransport;

/**
 * ASR（语音识别）provider 调用接缝。
 *
 * <p><b>为什么是接缝而不是完整 provider 库</b>：asr/check 端点的行为验证需要真实
 * 出站调用；所有 ASR 厂商共用 OpenAI 兼容 /v1/audio/transcriptions API，
 * 故以一份薄实现作为缺省实现；将来扩展其他 provider 时只需替换本接口的实现。</p>
 *
 * <p><b>为什么在 {@code llm/asr}</b>：它是"按模型配置调 provider"的能力（与 {@code llm/chat}
 * 同层，复用 {@code LlmTransport} 出站），消费方横跨初始化（asr/check）、模型调试、
 * 会话（临时文档转写）、检索（VLM 错误文案复用其静态映射）——原先落在 initialization
 * 时被后三个域反向依赖成环。</p>
 *
 * <p>缺省实现的线上行为（错误文案等已被 golden 钉死，不可改字）：</p>
 * <ul>
 *   <li>构造期 SSRF 校验，失败报
 *       "base URL SSRF check failed: ..."；</li>
 *   <li>multipart POST {baseURL}/audio/transcriptions，字段序
 *       file → model → response_format=verbose_json → language；
 *       300s 超时；</li>
 *   <li>非 2xx 错误文案：{@code HTTP <状态行>: <详情>}——详情取错误体的
 *       {@code error.message}（字符串或数组），取不到时退回 body 原文；</li>
 *   <li>200 响应 {text, segments}：text 去首尾空白。</li>
 * </ul>
 */
public interface AsrTranscriber {

    /** ASR 调用配置（本端点只用这些字段）。 */
    record AsrConfig(String baseUrl, String modelName, String apiKey, String modelId,
                     String language, Map<String, String> customHeaders) {}

    /** 转写结果（text + segments；segments 为空时省略）。 */
    record TranscriptionResult(String text, List<Segment> segments) {
        public record Segment(double start, double end, String text) {}
    }

    /** 构造失败异常（message 已含 "base URL SSRF check failed" 等前缀原文，文案钉死）。 */
    class AsrCreateException extends RuntimeException {
        public AsrCreateException(String message) { super(message); }
    }

    /** 调用失败异常（message 按 "ASR transcription request failed: ..." 包裹，文案钉死）。 */
    class AsrTranscribeException extends RuntimeException {
        public AsrTranscribeException(String message) { super(message); }
    }

    /**
     * 转写入口。构造失败抛 {@link AsrCreateException}，
     * 调用失败抛 {@link AsrTranscribeException}，成功返回结果。
     */
    TranscriptionResult transcribe(AsrConfig config, byte[] audioBytes, String fileName);

    // ==================================================================
    // 缺省实现：OpenAI 兼容 transcription
    // ==================================================================

    class OpenAiAsrTranscriber implements AsrTranscriber {

        /** 300s 超时（audio transcription can be slow）。 */
        private static final Duration ASR_DEFAULT_TIMEOUT = Duration.ofSeconds(300);

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final SsrfGuard ssrfGuard;

        public OpenAiAsrTranscriber(SsrfGuard ssrfGuard) {
            this.ssrfGuard = ssrfGuard;
        }

        @Override
        public TranscriptionResult transcribe(AsrConfig config, byte[] audioBytes, String fileName) {
            // 构造期 SSRF 校验
            if (config.baseUrl() != null && !config.baseUrl().isEmpty()) {
                try {
                    ssrfGuard.validateURLForSSRF(config.baseUrl());
                } catch (RuntimeException e) {
                    throw new AsrCreateException("base URL SSRF check failed: " + e.getMessage());
                }
            }
            if (audioBytes == null || audioBytes.length == 0) {
                throw new AsrTranscribeException("ASR transcription request failed: audio bytes are empty");
            }
            String name = fileName == null || fileName.isEmpty() ? "audio.mp3" : fileName;

            String url = config.baseUrl() + "/audio/transcriptions";
            String boundary = "go-openai-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, config, audioBytes, name);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + (config.apiKey() == null ? "" : config.apiKey()))
                    .timeout(ASR_DEFAULT_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (config.customHeaders() != null) {
                config.customHeaders().forEach(builder::header);
            }

            HttpResponse<InputStream> resp;
            try {
                resp = LlmTransport.send(builder.build());
            } catch (IOException e) {
                // 错误文案仿 url.Error 形态（Post "...": ...），格式钉死
                throw new AsrTranscribeException("ASR transcription request failed: Post \""
                        + url + "\": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AsrTranscribeException("ASR transcription request failed: interrupted");
            }
            byte[] respBody;
            try (InputStream stream = resp.body()) {
                respBody = stream == null ? new byte[0] : stream.readAllBytes();
            } catch (IOException e) {
                throw new AsrTranscribeException("ASR transcription request failed: " + e.getMessage());
            }
            int status = resp.statusCode();
            if (status != 200) {
                // 错误文案逐字节钉死（见类注释）
                throw new AsrTranscribeException(
                        "ASR transcription request failed: " + openAiErrorText(status, respBody));
            }
            JsonNode node;
            try {
                node = MAPPER.readTree(respBody);
            } catch (IOException e) {
                throw new AsrTranscribeException(
                        "ASR transcription request failed: decode response: " + e.getMessage());
            }
            String text = node.path("text").asText("").trim();
            List<TranscriptionResult.Segment> segments = new ArrayList<>();
            JsonNode segs = node.get("segments");
            if (segs != null && segs.isArray()) {
                for (JsonNode s : segs) {
                    segments.add(new TranscriptionResult.Segment(s.path("start").asDouble(0), s.path("end").asDouble(0),
                            s.path("text").asText("").trim()));
                }
            }
            return new TranscriptionResult(text, segments);
        }

        /** multipart 字段序：file → model → response_format → language。 */
        private static byte[] multipartBody(String boundary, AsrConfig config, byte[] audio, String fileName) {
            StringBuilder sb = new StringBuilder();
            String fileContentType = probeContentType(fileName);
            sb.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(fileName).append("\"\r\n")
                    .append("Content-Type: ").append(fileContentType).append("\r\n\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
            StringBuilder tail = new StringBuilder();
            tail.append("\r\n--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                    .append(config.modelName() == null ? "" : config.modelName()).append("\r\n")
                    .append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
                    .append("verbose_json\r\n");
            if (config.language() != null && !config.language().isEmpty()) {
                tail.append("--").append(boundary).append("\r\n")
                        .append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
                        .append(config.language()).append("\r\n");
            }
            tail.append("--").append(boundary).append("--\r\n");
            byte[] tailBytes = tail.toString().getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[head.length + audio.length + tailBytes.length];
            System.arraycopy(head, 0, out, 0, head.length);
            System.arraycopy(audio, 0, out, head.length, audio.length);
            System.arraycopy(tailBytes, 0, out, head.length + audio.length, tailBytes.length);
            return out;
        }

        /** 粗略 MIME 推断（按常见后缀）。 */
        private static String probeContentType(String fileName) {
            String lower = fileName.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".wav")) {
                return "audio/wav";
            }
            if (lower.endsWith(".flac")) {
                return "audio/flac";
            }
            if (lower.endsWith(".ogg")) {
                return "audio/ogg";
            }
            return "audio/mpeg";
        }

        /**
         * 非 2xx 错误文案：{@code HTTP <状态行>: <详情>}。
         * 详情优先取正规错误体里的 {@code error.message}（字符串或数组）；
         * 取不到（解析失败 / 无该字段 / 空体）时退回 body 原文。
         */
        public static String openAiErrorText(int statusCode, byte[] body) {
            String statusLine = statusCode + " " + reasonPhrase(statusCode);
            String rawBody = new String(body, StandardCharsets.UTF_8);
            String detail = null;
            if (body.length > 0) {
                try {
                    JsonNode root = MAPPER.readTree(body);
                    JsonNode err = root == null ? null : root.get("error");
                    if (err != null && err.isObject()) {
                        JsonNode msg = err.get("message");
                        if (msg != null && !msg.isNull()) {
                            if (msg.isTextual()) {
                                detail = msg.asText();
                            } else if (msg.isArray()) {
                                List<String> parts = new ArrayList<>();
                                msg.forEach(m -> parts.add(m.asText()));
                                detail = String.join(", ", parts);
                            }
                        }
                    }
                } catch (IOException ignored) {
                    detail = null;
                }
            }
            if (detail == null || detail.isEmpty()) {
                detail = rawBody;
            }
            return "HTTP " + statusLine + ": " + detail;
        }

        /** status 行短语表（"status: 200 OK" 段用）。 */
        static String reasonPhrase(int statusCode) {
            return switch (statusCode) {
                case 100 -> "Continue";
                case 101 -> "Switching Protocols";
                case 200 -> "OK";
                case 201 -> "Created";
                case 202 -> "Accepted";
                case 204 -> "No Content";
                case 301 -> "Moved Permanently";
                case 302 -> "Found";
                case 303 -> "See Other";
                case 304 -> "Not Modified";
                case 307 -> "Temporary Redirect";
                case 308 -> "Permanent Redirect";
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 402 -> "Payment Required";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 405 -> "Method Not Allowed";
                case 406 -> "Not Acceptable";
                case 408 -> "Request Timeout";
                case 409 -> "Conflict";
                case 410 -> "Gone";
                case 413 -> "Request Entity Too Large";
                case 415 -> "Unsupported Media Type";
                case 422 -> "Unprocessable Entity";
                case 429 -> "Too Many Requests";
                case 500 -> "Internal Server Error";
                case 501 -> "Not Implemented";
                case 502 -> "Bad Gateway";
                case 503 -> "Service Unavailable";
                case 504 -> "Gateway Timeout";
                default -> "";
            };
        }
    }
}
