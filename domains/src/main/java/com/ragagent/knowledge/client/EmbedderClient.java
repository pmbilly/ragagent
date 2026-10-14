package com.ragagent.knowledge.client;

import com.ragagent.common.web.JsonMappers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.model.domain.Model;
import org.springframework.stereotype.Service;
import java.io.IOException;
import com.ragagent.platform.model.EmbeddingGateway;
import com.ragagent.contract.model.ModelFacts;

/**
 * 最小 OpenAI 兼容 embedding 客户端。
 *
 * POST {base_url}/embeddings，Bearer api_key；输入 batch 文本，返回 float 向量。
 */
@Service
public class EmbedderClient implements EmbeddingGateway {

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final HttpClient http;

    public EmbedderClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    /** 测试缝：注入桩 HttpClient 以覆盖响应校验路径（生产装配走无参构造）。 */
    EmbedderClient(HttpClient http) {
        this.http = http;
    }

    /** base_url/api_key 来自模型参数 */
    public record EmbedConfig(String baseUrl, String apiKey, String modelName) {}

    public static EmbedConfig configFrom(Model model) {
        var p = model.getParameters();
        return configFrom(new ModelFacts(model.getId(), model.getName(),
                p == null || p.getBaseUrl() == null ? "" : p.getBaseUrl(),
                p == null || p.getApiKey() == null ? "" : p.getApiKey()));
    }

    /** 端口载荷版本：给不持有 {@code Model} 实体的调用方（如 retrieval 的查询嵌入）。 */
    public static EmbedConfig configFrom(ModelFacts f) {
        String base = f.baseUrl() == null ? "" : f.baseUrl();
        if (base.isEmpty()) {
            base = "https://api.openai.com/v1";
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return new EmbedConfig(base, f.apiKey() == null ? "" : f.apiKey(),
                f.name() == null ? "" : f.name());
    }

    /**
     * 能力端口的实现（{@link EmbeddingGateway}）：按模型事实嵌入文本。
     *
     * <p>本类既有的 {@code embedBatch} 声明受检异常（Jackson 的 parse 异常），端口面
     * 不带受检异常，故在此收敛为同名消息的运行时异常——调用方看到的文案不变。</p>
     */
    @Override
    public List<float[]> embed(ModelFacts model, List<String> texts) {
        try {
            return embedBatch(configFrom(model), texts);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** 批量嵌入 */
    public List<float[]> embedBatch(EmbedConfig config, List<String> texts) throws Exception {
        var input = MAPPER.createArrayNode();
        texts.forEach(input::add);
        var body = MAPPER.createObjectNode();
        body.put("model", config.modelName());
        body.set("input", input);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/embeddings"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        if (!config.apiKey().isEmpty()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        HttpResponse<String> resp;
        try {
            resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            //  %w"（url.Error 含方法与地址）：JDK 裸 ConnectException
            // 常无 message，兜底类名；dial tcp 等传输层内文属已记录的掩码 DIFF 族。
            throw new IllegalStateException(
                    "send request: Post \"" + config.baseUrl() + "/embeddings\": " + ioDetail(e), e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("embedding request failed: HTTP " + resp.statusCode()
                    + " " + abbreviate(resp.body()));
        }
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new IllegalStateException("embedding response missing data: " + abbreviate(resp.body()));
        }
        List<float[]> out = new ArrayList<>(texts.size());
        // K10a：按响应声明的 index 归位并校验数量/维度。下游 VectorStoreService.saveIndexRows
        // 是 rows[i] ↔ vectors[i] 按位置配行——顺序错/少条/混维都会静默写错向量（比报错危险）。
        float[][] ordered = new float[texts.size()][];
        int seen = 0;
        int dimension = -1;
        for (JsonNode item : data) {
            JsonNode emb = item.get("embedding");
            if (emb == null || !emb.isArray() || emb.isEmpty()) {
                throw new IllegalStateException("embedding item missing vector");
            }
            float[] vector = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vector[i] = (float) emb.get(i).asDouble();
            }
            if (dimension < 0) {
                dimension = vector.length;
            } else if (dimension != vector.length) {
                throw new IllegalStateException("embedding dimension mismatch within batch: expected "
                        + dimension + " but got " + vector.length);
            }
            JsonNode idxNode = item.get("index");
            int index = idxNode != null && idxNode.isInt() ? idxNode.asInt() : seen;
            if (index < 0 || index >= ordered.length) {
                throw new IllegalStateException("embedding index out of range: " + index
                        + " (batch size " + ordered.length + ")");
            }
            if (ordered[index] != null) {
                throw new IllegalStateException("embedding index duplicated: " + index);
            }
            ordered[index] = vector;
            seen++;
        }
        if (seen != texts.size()) {
            throw new IllegalStateException("embedding response size mismatch: expected "
                    + texts.size() + " vector(s) but got " + seen);
        }
        for (float[] vector : ordered) {
            out.add(vector);
        }
        return out;
    }

    private static String ioDetail(IOException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
