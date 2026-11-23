package com.ragagent.model.assembler;

import com.ragagent.model.controller.vo.ModelCredentialFieldMetaVo;
import com.ragagent.model.controller.vo.ModelCredentialsRespVo;
import com.ragagent.model.controller.vo.ModelDebugRespVo;
import com.ragagent.model.controller.vo.ModelDetailRespVo;
import com.ragagent.model.controller.vo.ModelParametersRespVo;
import com.ragagent.model.controller.vo.ModelProviderRespVo;
import com.ragagent.model.dto.ModelCredentialFieldMetadata;
import com.ragagent.model.dto.ModelCredentialsResponse;
import com.ragagent.model.dto.ModelDebugResult;
import com.ragagent.model.dto.ModelDetailResponse;
import com.ragagent.model.dto.ModelParametersDto;
import com.ragagent.model.dto.ModelProviderDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模型域响应 DTO 到 HTTP VO 的装配。 */
public final class ModelAssembler {

    private ModelAssembler() {
    }

    /** 装配模型详情；凭据可见性由源 DTO 的 null 语义决定，此处原样透传。 */
    public static ModelDetailRespVo toDetailVo(ModelDetailResponse src) {
        if (src == null) {
            return null;
        }
        return new ModelDetailRespVo(
                src.id(),
                src.tenantId(),
                src.name(),
                src.displayName(),
                src.type(),
                src.source(),
                src.description(),
                toParametersVo(src.parameters()),
                src.isDefault(),
                src.isBuiltin(),
                src.status(),
                src.createdAt(),
                src.updatedAt(),
                toCredentialMap(src.credentials()));
    }

    /** 批量装配模型详情，保持原有顺序。 */
    public static List<ModelDetailRespVo> toDetailVoList(List<ModelDetailResponse> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(ModelAssembler::toDetailVo).toList();
    }

    /** 装配提供方列表项。 */
    public static ModelProviderRespVo toProviderVo(ModelProviderDto src) {
        if (src == null) {
            return null;
        }
        return new ModelProviderRespVo(
                src.value(), src.label(), src.description(), src.defaultUrls(), src.modelTypes());
    }

    /** 批量装配提供方列表项，保持注册表声明序。 */
    public static List<ModelProviderRespVo> toProviderVoList(List<ModelProviderDto> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(ModelAssembler::toProviderVo).toList();
    }

    /** 装配凭据子资源。 */
    public static ModelCredentialsRespVo toCredentialsVo(ModelCredentialsResponse src) {
        if (src == null) {
            return null;
        }
        return new ModelCredentialsRespVo(toCredentialMap(src.fields()));
    }

    /** 装配调试结果；{@code rawResponse} 多态原样透传。 */
    public static ModelDebugRespVo toDebugVo(ModelDebugResult src) {
        if (src == null) {
            return null;
        }
        return new ModelDebugRespVo(
                src.ok(),
                src.elapsedMs(),
                src.error(),
                src.request(),
                src.rawResponse(),
                src.observations());
    }

    private static ModelParametersRespVo toParametersVo(ModelParametersDto src) {
        if (src == null) {
            return null;
        }
        ModelParametersDto.EmbeddingParametersDTO embedding = src.embeddingParameters();
        return new ModelParametersRespVo(
                src.baseUrl(),
                src.interfaceType(),
                embedding == null
                        ? null
                        : new ModelParametersRespVo.EmbeddingParametersRespVo(
                                embedding.dimension(),
                                embedding.truncatePromptTokens(),
                                embedding.supportsDimensionOverride()),
                src.parameterSize(),
                src.provider(),
                src.extraConfig(),
                src.customHeaders(),
                src.supportsVision(),
                src.contextWindow(),
                src.maxOutputTokens(),
                src.maxConcurrency(),
                src.appId());
    }

    private static Map<String, ModelCredentialFieldMetaVo> toCredentialMap(
            Map<String, ModelCredentialFieldMetadata> src) {
        if (src == null) {
            return null;
        }
        Map<String, ModelCredentialFieldMetaVo> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, v == null ? null : new ModelCredentialFieldMetaVo(v.configured())));
        return out;
    }
}
