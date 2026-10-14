package com.ragagent.model.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import com.ragagent.model.domain.Model;
import com.ragagent.common.model.ModelParameters;

/**
 * 模型响应（HTTP 视图；JSON 字段名即 Java 字段名，输出序 = 组件声明序）。
 *
 * <p>秘密字段（apiKey/appSecret）在构造上就不存在；内置模型对非系统管理员
 * 剥离 baseUrl/extraConfig/customHeaders/appId（见 {@link #from}）。
 * {@code credentials} 为 null 表示不可见（内置模型对非系统管理员）。</p>
 */
public record ModelDetailResponse(
        String id,
        long tenantId,
        String name,
        String displayName,
        String type,
        String source,
        String description,
        ModelParametersDTO parameters,
        boolean isDefault,
        boolean isBuiltin,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, ModelCredentialFieldMetadata> credentials) {

    /**
     * @param canViewIntegrationSecrets Admin+ 可见完整参数
     * @param canManageBuiltin 系统管理员可管内置模型
     */
    public static ModelDetailResponse from(Model m,
                                     boolean canViewIntegrationSecrets,
                                     boolean canManageBuiltin) {
        ModelParameters p = m.getParameters();
        // 0 / 空串 = 未设置 → null（"用后端默认值"的显式语义）
        Integer contextWindow = p.getContextWindow() == 0 ? null : p.getContextWindow();
        Integer maxOutputTokens = p.getMaxOutputTokens() == 0 ? null : p.getMaxOutputTokens();
        Integer maxConcurrency = p.getMaxConcurrency() == 0 ? null : p.getMaxConcurrency();
        String appId = p.getAppId().isEmpty() ? null : p.getAppId();
        ModelParametersDTO params = new ModelParametersDTO(
                p.getBaseUrl(), p.getInterfaceType(),
                new ModelParametersDTO.EmbeddingParametersDTO(
                        p.getEmbeddingParameters().getDimension(),
                        p.getEmbeddingParameters().getTruncatePromptTokens(),
                        p.getEmbeddingParameters().isSupportsDimensionOverride()),
                p.getParameterSize(), p.getProvider(), p.getExtraConfig(), p.getCustomHeaders(),
                p.isSupportsVision(), contextWindow, maxOutputTokens, maxConcurrency, appId);

        if (!canViewIntegrationSecrets && !canManageBuiltin) {
            params = params.withSecretsStripped();
        }
        if (m.isIsBuiltin() && !canManageBuiltin) {
            params = params.withBuiltinStripped();
        }

        Map<String, ModelCredentialFieldMetadata> creds = null;
        if (!m.isIsBuiltin() || canManageBuiltin) {
            // 字段标识符恒为 api_key / app_secret（也是 DELETE /credentials/{field} 的取值域）
            creds = new java.util.LinkedHashMap<>();
            creds.put("apiKey", new ModelCredentialFieldMetadata(!p.getApiKey().isEmpty()));
            creds.put("appSecret", new ModelCredentialFieldMetadata(!p.getAppSecret().isEmpty()));
        }
        return new ModelDetailResponse(
                m.getId(), m.getTenantId() == null ? 0 : m.getTenantId(),
                m.getName(), m.getDisplayName(), m.getType(), m.getSource(), m.getDescription(),
                params, m.isIsDefault(), m.isIsBuiltin(), m.getStatus(),
                m.getCreatedAt(), m.getUpdatedAt(), creds);
    }
}
