package com.ragagent.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * **检索编排只依赖中立类型**（`EngineTypes.IndexWithScore` / `EngineTypes.RetrieveResult`）。
 *
 * <p>此前编排（`HybridSearchService` / `HybridFusionOps` / `HybridResultOps` / `RetrievalObs`）
 * 直接说 `PgVectorRetrieveRepository.IndexHit` / `RetrieveResult`——那是**具体向量库适配器**的
 * 行形状；结果每换一个引擎都要在编排里映射一次（`toPgShape`），上层与 pg 实现绑死。</p>
 *
 * <p>本测试只扫 `retrieval` 包**本层**（不含 `retrieval.engine` 适配层——适配器当然知道自己的
 * 行形状）。</p>
 */
class RetrievalNeutralTypesTest {

    private static final Path ORCHESTRATION_DIR = Path.of("src/main/java/com/ragagent/retrieval");

    @Test
    @DisplayName("retrieval 编排层不得再引用 PgVectorRetrieveRepository（改用 EngineTypes 中立类型）")
    void orchestrationSpeaksNeutralTypesOnly() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(ORCHESTRATION_DIR)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    if (Files.readString(p).contains("PgVectorRetrieveRepository")) {
                        offenders.add(p.getFileName().toString());
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        assertThat(offenders)
                .as("编排层请用 EngineTypes.IndexWithScore / EngineTypes.RetrieveResult")
                .isEmpty();
    }
}
