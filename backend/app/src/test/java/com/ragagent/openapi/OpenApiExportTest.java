package com.ragagent.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OpenAPI 快照门：把 springdoc 生成的 /v3/api-docs 与入库快照逐字比对。
 *
 * <p><b>为什么</b>：1422 个金片钉的是"请求→响应字节"，OpenAPI 快照钉的是
 * "契约形状"（路径/方法/字段/schema）——后者在 PR diff 里肉眼可审，
 * 前者只有跑测试才能发现。两者互补。</p>
 *
 * <p><b>重录</b>：契约有意变更时跑
 * {@code mvn -pl backend/app test -Dtest=OpenApiExportTest -Dcontract.refresh=true}
 * 重写快照，随代码同提交。drift（快照与实际不一致）即红。</p>
 *
 * <p>{@code /v3/api-docs} 在 AuthFilter 白名单（AuthFilter.java:206）
 * 且不在 RbacInterceptor 的 path patterns（仅 /api/v1/**）内，无需鉴权。</p>
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiExportTest {

    private static final Path SNAPSHOT = Path.of("backend/app/src/test/resources/contracts/openapi-snapshot.json");
    private static final Path TARGET = Path.of("backend/app/target/openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiSnapshotMatchesCommitted() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        assertThat(actual).contains("\"openapi\"");

        // 始终写 target/ 供前端类型生成消费（frontend/packages/api-client 的输入）
        Files.createDirectories(TARGET.getParent());
        Files.writeString(TARGET, actual);

        if (Boolean.getBoolean("contract.refresh") || !Files.exists(SNAPSHOT)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual);
        }
        assertThat(actual)
                .as("OpenAPI 契约漂移：与 openapi-snapshot.json 不一致。有意变更请用 -Dcontract.refresh=true 重录并同提交")
                .isEqualTo(Files.readString(SNAPSHOT));
    }
}
