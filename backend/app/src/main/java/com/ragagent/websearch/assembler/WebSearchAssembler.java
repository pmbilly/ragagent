package com.ragagent.websearch.assembler;

import com.ragagent.websearch.controller.vo.WebSearchProviderRespVo;
import com.ragagent.websearch.controller.vo.WebSearchProviderTypeVo;
import com.ragagent.websearch.dto.WebSearchProviderResponse;
import com.ragagent.websearch.dto.WebSearchProviderTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 搜索域响应 DTO 到 HTTP VO 的装配。 */
public final class WebSearchAssembler {

    private WebSearchAssembler() {
    }

    /** 装配提供方资源行。 */
    public static WebSearchProviderRespVo toProviderVo(WebSearchProviderResponse src) {
        if (src == null) {
            return null;
        }
        return new WebSearchProviderRespVo(
                src.id,
                src.tenantId,
                src.name,
                src.provider,
                src.description,
                toParametersVo(src.parameters),
                src.isDefault,
                src.createdAt,
                src.updatedAt,
                toCredentialMap(src.credentials));
    }

    /** 批量装配提供方，保持原有顺序（空仓库是空列表）。 */
    public static List<WebSearchProviderRespVo> toProviderVoList(List<WebSearchProviderResponse> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(WebSearchAssembler::toProviderVo).toList();
    }

    /** 装配类型元数据列表，顺序即注册表声明序。 */
    public static List<WebSearchProviderTypeVo> toTypeVoList(List<WebSearchProviderTypes.TypeInfo> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(WebSearchAssembler::toTypeVo).toList();
    }

    /** 装配单个类型元数据。 */
    public static WebSearchProviderTypeVo toTypeVo(WebSearchProviderTypes.TypeInfo src) {
        if (src == null) {
            return null;
        }
        return new WebSearchProviderTypeVo(
                src.id,
                src.name,
                src.requiresApiKey,
                src.supportsOptionalApiKey,
                src.requiresEngineId,
                src.requiresBaseUrl,
                src.supportsProxy,
                src.description,
                src.docsUrl,
                src.configFields);
    }

    private static WebSearchProviderRespVo.ParametersVo toParametersVo(
            WebSearchProviderResponse.ParametersDTO src) {
        if (src == null) {
            return null;
        }
        return new WebSearchProviderRespVo.ParametersVo(
                src.engineId, src.baseUrl, src.proxyUrl, src.extraConfig);
    }

    private static Map<String, WebSearchProviderRespVo.CredentialFieldMetaVo> toCredentialMap(
            Map<String, WebSearchProviderResponse.CredentialFieldMetadata> src) {
        if (src == null) {
            return null;
        }
        Map<String, WebSearchProviderRespVo.CredentialFieldMetaVo> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k,
                v == null ? null : new WebSearchProviderRespVo.CredentialFieldMetaVo(v.configured)));
        return out;
    }
}
