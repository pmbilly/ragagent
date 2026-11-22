package com.ragagent.framework.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 跨域词汇与租户配置面的成员白名单棘轮：只许减，不许增。
 *
 * <p>framework 是基础设施层；这两个包是「确实无处安放」的形状的最后一站。
 * 新增成员前必须先论证没有更合适的属主（域 api / 引擎 api），并在本测试登记理由；
 * 成员迁出后应同步删除白名单条目（棘轮只减）。</p>
 */
class VocabularyManifestTest {

    /** vocabulary 白名单（终态 8）：MessageView 簇已迁 channel/api 与 agentengine/api（channel 合并批）；余下为晶格孤儿（双引擎消费）与待解钉件。 */
    private static final List<String> VOCABULARY_ALLOW =
            List.of();

    /** tenant 白名单：请求级租户上下文（原 access 包并入）+ 租户配置载荷与 API Key 模型（RbacInterceptor→TenantAPIKeyScope 钉链，随 Sa-Token 批整链迁 auth）。 */
    private static final List<String> TENANT_ALLOW =
            List.of("APIKeyCapability", "APIKeyScopeContext", "APIKeyScopeType",
                    "APIPrincipalConfig", "StorageEngineConfig", "TenantAPIKeyScope",
                    "TenantConfigLookup", "TenantRole",
                    "TenantContext", "TenantContextSnapshot", "TenantScope", "TenantScopes");

    private static List<String> mainClasses(String pkg) {
        Path dir = Path.of("src/main/java/com/ragagent/framework/" + pkg);
        if (!Files.isDirectory(dir)) {
            return List.of(); // 包已删除（vocabulary 归零）——目录不存在等价于无成员
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".java") && !"package-info.java".equals(n))
                    .map(n -> n.substring(0, n.length() - ".java".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AssertionError("无法列出 " + pkg + " 主码目录", e);
        }
    }

    @Test
    void vocabularyMembersExactlyMatchManifest() {
        assertThat(mainClasses("vocabulary"))
                .as("vocabulary 成员 = 白名单（棘轮只减不增）")
                .containsExactlyInAnyOrderElementsOf(VOCABULARY_ALLOW);
    }

    @Test
    void tenantMembersExactlyMatchManifest() {
        assertThat(mainClasses("tenant"))
                .as("tenant 成员 = 白名单（棘轮只减不增）")
                .containsExactlyInAnyOrderElementsOf(TENANT_ALLOW);
    }
}
