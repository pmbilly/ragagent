package com.ragagent.llm.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 注册表完整性：{@link ProviderName} 的每个枚举值都必须注册、且出现在 {@link ProviderRegistry#list()}
 * （顺序由 {@code allProviders()} 决定）。
 *
 * <p>HTTP 目录（{@code ModelProviderCatalog}）由 {@code list()} 生成，金片钉住它的字节；
 * 本测试把"漏注册 / 漏进 allProviders"的失败提前到元数据层，报错比金片比对更直接。</p>
 */
class ProviderRegistryCompletenessTest {

    @Test
    @DisplayName("ProviderName 全集 == 注册表可列出全集；数量不一致即红")
    void everyProviderNameIsRegisteredAndListed() {
        Set<String> declared = Arrays.stream(ProviderName.values())
                .map(ProviderName::value)
                .collect(Collectors.toSet());
        List<String> listed = ProviderRegistry.list().stream()
                .map(info -> info.name().value())
                .toList();

        assertEquals(declared.size(), listed.size(),
                "ProviderName 声明 " + declared.size() + " 个厂商，list() 给出 " + listed.size() + " 个");
        assertEquals(declared, Set.copyOf(listed), "注册表内容与 ProviderName 声明不一致");
    }
}
