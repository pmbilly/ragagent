package com.ragagent.knowledge.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * K10a：embedding 响应必须**按 index 归位**并校验数量/维度。
 *
 * <p>下游 {@code VectorStoreService.saveIndexRows} 是 {@code rows[i] ↔ vectors[i]} 按位置配行——
 * 顺序错、少条、混维都会**静默写错向量**（比报错危险）。此前的实现按响应数组顺序拼装、
 * 不消费 {@code index}、不校验数量与维度。</p>
 */
class EmbedderClientResponseValidationTest {

    private static final EmbedderClient.EmbedConfig CONFIG =
            new EmbedderClient.EmbedConfig("http://embed.test/v1", "k", "m");

    @SuppressWarnings("unchecked")
    private static EmbedderClient clientReturning(String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);
        return new EmbedderClient(http);
    }

    @Test
    @DisplayName("K10a：响应乱序（index=1 在前）必须按 index 归位——不许按数组序错配")
    void outOfOrderIndexIsHonored() throws Exception {
        EmbedderClient client = clientReturning("""
                {"data":[{"index":1,"embedding":[9.0,9.0]},{"index":0,"embedding":[1.0,1.0]}]}
                """);

        List<float[]> out = client.embedBatch(CONFIG, List.of("a", "b"));

        assertThat(out).hasSize(2);
        assertThat(out.get(0)).containsExactly(1.0f, 1.0f);
        assertThat(out.get(1)).containsExactly(9.0f, 9.0f);
    }

    @Test
    @DisplayName("K10a：响应条数少于输入必须抛（少条会让下游按位置错配）")
    void sizeMismatchThrows() throws Exception {
        EmbedderClient client = clientReturning("""
                {"data":[{"index":0,"embedding":[1.0,1.0]}]}
                """);

        assertThatThrownBy(() -> client.embedBatch(CONFIG, List.of("a", "b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size mismatch");
    }

    @Test
    @DisplayName("K10a：批内维度不一致必须抛（混维会让同一列写进不同维度向量）")
    void dimensionMismatchThrows() throws Exception {
        EmbedderClient client = clientReturning("""
                {"data":[{"index":0,"embedding":[1.0,1.0]},{"index":1,"embedding":[1.0,1.0,1.0]}]}
                """);

        assertThatThrownBy(() -> client.embedBatch(CONFIG, List.of("a", "b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    @DisplayName("K10a：index 越界/重复必须抛")
    void invalidIndexThrows() throws Exception {
        EmbedderClient outOfRange = clientReturning("""
                {"data":[{"index":3,"embedding":[1.0,1.0]}]}
                """);
        assertThatThrownBy(() -> outOfRange.embedBatch(CONFIG, List.of("a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out of range");

        EmbedderClient duplicated = clientReturning("""
                {"data":[{"index":0,"embedding":[1.0,1.0]},{"index":0,"embedding":[2.0,2.0]}]}
                """);
        assertThatThrownBy(() -> duplicated.embedBatch(CONFIG, List.of("a", "b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicated");
    }

    @Test
    @DisplayName("回归：顺序正确（含缺 index 时按数组序）照常返回")
    void happyPathStillWorks() throws Exception {
        EmbedderClient client = clientReturning("""
                {"data":[{"index":0,"embedding":[1.0,2.0]},{"embedding":[3.0,4.0]}]}
                """);

        List<float[]> out = client.embedBatch(CONFIG, List.of("a", "b"));

        assertThat(out).hasSize(2);
        assertThat(out.get(0)).containsExactly(1.0f, 2.0f);
        assertThat(out.get(1)).containsExactly(3.0f, 4.0f);
    }
}
