package com.ragagent.model.service;

import com.ragagent.llm.provider.ModelType;
import com.ragagent.llm.provider.ProviderInfo;
import com.ragagent.llm.provider.ProviderRegistry;
import com.ragagent.model.dto.ModelProviderDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * 模型厂商 HTTP 目录。
 *
 * <p><b>单一数据源</b>：厂商的展示名、描述、默认 URL 与模型类型只写在 {@code model-runtime}
 * 的 Provider 实现里，本类从 {@link ProviderRegistry#list()} 取元数据，只做表示层转换
 * （枚举字面量 → 前端字符串）。新增厂商不再需要改这里。</p>
 *
 * <p>对外字节由金片 {@code contracts/model-providers.json} 钉住：顺序 = 注册表声明序，
 * {@code defaultUrls} 经 {@link TreeMap} 输出为字母序。</p>
 */
@Component
public class ModelProviderCatalog {

    /** 后端类型 → 前端字符串 */
    public static String toFrontend(String backendType) {
        return switch (backendType) {
            case "KnowledgeQA" -> "chat";
            case "Embedding" -> "embedding";
            case "Rerank" -> "rerank";
            case "VLLM" -> "vllm";
            case "ASR" -> "asr";
            default -> backendType;
        };
    }

    /** 前端查询参数 → 后端类型 */
    public static String queryToBackend(String modelType) {
        return switch (modelType) {
            case "chat" -> "KnowledgeQA";
            case "embedding" -> "Embedding";
            case "rerank" -> "Rerank";
            case "vllm" -> "VLLM";
            case "asr" -> "ASR";
            default -> modelType;
        };
    }

    private record ProviderEntry(String value, String label, String description,
                                 Map<String, String> defaultUrls, List<String> modelTypes,
                                 List<String> backendTypes) {}

    private static final List<ProviderEntry> ENTRIES = ProviderRegistry.list().stream()
            .map(ModelProviderCatalog::toEntry)
            .toList();

    private static ProviderEntry toEntry(ProviderInfo info) {
        Map<String, String> urls = new LinkedHashMap<>();
        info.defaultUrls().forEach((type, url) -> urls.put(toFrontend(type.value()), url));
        return new ProviderEntry(
                info.name().value(),
                info.displayName(),
                info.description(),
                urls,
                info.modelTypes().stream().map(t -> toFrontend(t.value())).toList(),
                info.modelTypes().stream().map(ModelType::value).toList());
    }

    /** 全量（注册表顺序）。 */
    public List<ModelProviderDto> list() {
        return ENTRIES.stream().map(ModelProviderCatalog::toDTO).toList();
    }

    /** 按后端类型过滤，保持注册表顺序。 */
    public List<ModelProviderDto> listByModelType(String backendType) {
        return ENTRIES.stream()
                .filter(e -> e.backendTypes().contains(backendType))
                .map(ModelProviderCatalog::toDTO)
                .toList();
    }

    private static ModelProviderDto toDTO(ProviderEntry e) {
        // defaultUrls 为 map：TreeMap 保证 JSON key 字母序（输出稳定字节）
        return new ModelProviderDto(e.value(), e.label(), e.description(),
                new TreeMap<>(e.defaultUrls()), e.modelTypes());
    }
}
