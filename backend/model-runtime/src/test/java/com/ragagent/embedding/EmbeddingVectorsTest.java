package com.ragagent.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ragagent.platform.web.ProviderJson;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * embedding 响应校验：条数、空向量、批内维度、index 归位与越界/重复。
 *
 * <p>校验失败必须报错而不是返回错位向量——pg 写库按位置配行，静默错位比失败更难查。</p>
 */
class EmbeddingVectorsTest {

    private static float[] vec(float... values) {
        return values;
    }

    @Test
    @DisplayName("OpenAI 族：按 index 归位（响应乱序也按位置还原）")
    void ordersByIndex() {
        String body = """
                {"data":[{"index":1,"embedding":[3.0,4.0]},{"index":0,"embedding":[1.0,2.0]}]}
                """;
        List<float[]> vectors = EmbeddingVectors.fromData(
                ProviderJson.parse(body).path("data"), body, 2);
        assertArrayEquals(vec(1.0f, 2.0f), vectors.get(0));
        assertArrayEquals(vec(3.0f, 4.0f), vectors.get(1));
    }

    @Test
    @DisplayName("缺 data / 空向量 / 维度不一致 / 条数不符 都报错")
    void rejectsMalformedResponses() {
        String missing = "{\"object\":\"list\"}";
        assertEquals("embedding response missing data: " + missing,
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(missing).path("data"), missing, 1)).getMessage());

        String emptyVector = "{\"data\":[{\"embedding\":[]}]}";
        assertEquals("embedding item missing vector",
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(emptyVector).path("data"), emptyVector, 1)).getMessage());

        String mismatch = "{\"data\":[{\"embedding\":[1.0]},{\"embedding\":[1.0,2.0]}]}";
        assertEquals("embedding dimension mismatch within batch: expected 1 but got 2",
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(mismatch).path("data"), mismatch, 2)).getMessage());

        String shortResponse = "{\"data\":[{\"embedding\":[1.0]}]}";
        assertEquals("embedding response size mismatch: expected 2 vector(s) but got 1",
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(shortResponse).path("data"), shortResponse, 2))
                        .getMessage());
    }

    @Test
    @DisplayName("index 越界与重复都报错")
    void rejectsBadIndexes() {
        String outOfRange = "{\"data\":[{\"index\":2,\"embedding\":[1.0]}]}";
        assertEquals("embedding index out of range: 2 (batch size 1)",
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(outOfRange).path("data"), outOfRange, 1)).getMessage());

        String duplicated = "{\"data\":[{\"index\":0,\"embedding\":[1.0]},"
                + "{\"index\":0,\"embedding\":[2.0]}]}";
        assertEquals("embedding index duplicated: 0",
                assertThrows(IllegalStateException.class, () -> EmbeddingVectors.fromData(
                        ProviderJson.parse(duplicated).path("data"), duplicated, 2)).getMessage());
    }

    @Test
    @DisplayName("非 OpenAI 族：check 校验条数、空向量与维度；单条同理")
    void checksNativeProviderVectors() {
        assertEquals("embedding response size mismatch: expected 2 vector(s) but got 1",
                assertThrows(IllegalStateException.class,
                        () -> EmbeddingVectors.check(List.of(vec(1.0f)), 2)).getMessage());
        assertEquals("embedding item missing vector",
                assertThrows(IllegalStateException.class,
                        () -> EmbeddingVectors.check(java.util.Arrays.asList(vec(1.0f), null), 2))
                        .getMessage());
        assertEquals("embedding item missing vector",
                assertThrows(IllegalStateException.class,
                        () -> EmbeddingVectors.checkOne(new float[0])).getMessage());
    }
}
