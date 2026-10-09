package com.ragagent.auth.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;
import com.ragagent.common.web.ToolJson;
import com.ragagent.common.web.RequestFields;

/**
 * 租户目录的绑定错误形态助手（全静态）：
 * 请求绑定 mapper（EOF/语法/类型错误 → legacy 文案）、
 * validator 错误文案、Unicode trim。四簇协作者共用。
 */
final class TenantBindSupport {

    /** 请求绑定 mapper：忽略未知字段（FAIL_ON_UNKNOWN off）；
     *  JavaTimeModule 供含 created_at 等时间字段的全路径往返 */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    /** string 字段类型检查；违规返回字段级类型文案，否则 null。 */
    static String stringFieldTypeError(com.fasterxml.jackson.databind.JsonNode root, String field) {
        com.fasterxml.jackson.databind.JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.isTextual()) {
            return null;
        }
        return RequestFields.wrongType(field, "string", ToolJson.nodeTypeLabel(node));
    }
    // ── 绑定与错误形态（对照 AuthController 的既有模式） ─────────────────────

    /**
     * 请求体绑定：空 body → details "No content to map due to end-of-input"；语法/类型错误 →
     * legacy 文案（标准 Jackson 消息）。body 是 JSON null 字面量时
     * 不报错——Jackson readValue 返回 null，调用方按零值处理。
     */
    static <T> T bindBody(String rawBody, Class<T> type, String message) {
        if (rawBody == null || rawBody.isBlank()) {
            throw invalidParams(message, "No content to map due to end-of-input");
        }
        try {
            return MAPPER.readValue(rawBody, type);
        } catch (Exception e) {
            throw invalidParams(message, e.getMessage());
        }
    }

    static String bindingError(String field, String tag) {
        return RequestFields.message(field, tag);
    }

    /** 校验失败：400 + details 原文。 */
    static BizException invalidParams(String message, String details) {
        return new BizException(AppError.validation(message).withDetails(details));
    }

    /** 去除首尾 Unicode 空白（null → 空串）。 */
    static String trimGo(String s) {
        return s == null ? "" : s.strip();
    }
}
