package com.ragagent.embedchannel.controller;

import com.ragagent.contract.error.BizException;
import com.ragagent.embedchannel.controller.filter.EmbedAuthFilter;
import com.ragagent.embedchannel.domain.EmbedChannelEntity;
import com.ragagent.embedchannel.service.EmbedChannelService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Embed 渠道行与请求上下文投影：控制器只做路由，不持有实体。 */
public final class EmbedChannelRows {

    private EmbedChannelRows() {
    }

    /**
     * 渠道行视图：键字母序。publishToken 非空才带键。
     */
    public static Map<String, Object> row(EmbedChannelEntity ch, boolean withPublishToken) {
        return row(ch, ch.getPublishToken() == null ? "" : ch.getPublishToken(), withPublishToken);
    }

    /**
     * 渠道行视图（键名＝实体字段名、无 {@code {data,success}} 信封）。
     *
     * <p>唯一的**条件键**是 {@code publishToken}：管理详情/创建/轮换带它、列表行不带——
     * 这是**授权边界**（列表里泄漏 publish token 等于把渠道会话签发权发给任何读列表的人），
     * 不是"有时出现有时消失"的数据条件键，故刻意保留。</p>
     */
    public static Map<String, Object> row(EmbedChannelEntity ch, String token) {
        return row(ch, token, true);
    }

    public static Map<String, Object> row(EmbedChannelEntity ch, String token, boolean includeToken) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agentId", ch.getAgentId());
        m.put("allowFileUpload", ch.isAllowFileUpload());
        m.put("allowWebSearch", ch.isAllowWebSearch());
        m.put("allowedOrigins", originsJson(EmbedChannelService.allowedOriginsList(ch)));
        m.put("createdAt", ch.getCreatedAt());
        m.put("defaultLocale", ch.getDefaultLocale());
        m.put("enabled", ch.isEnabled());
        m.put("hasWebhookSecret", ch.getWebhookSecret() != null && !ch.getWebhookSecret().isEmpty());
        m.put("headerTitleMode", EmbedChannelService.normalizeHeaderTitleMode(ch.getHeaderTitleMode()));
        m.put("id", ch.getId());
        m.put("launcherIcon", ch.getLauncherIcon());
        m.put("name", ch.getName());
        m.put("pageTitle", ch.getPageTitle());
        m.put("primaryColor", ch.getPrimaryColor());
        if (includeToken && token != null && !token.isEmpty()) {
            m.put("publishToken", token);
        }
        m.put("rateLimitPerDay", ch.getRateLimitPerDay());
        m.put("rateLimitPerMinute", ch.getRateLimitPerMinute());
        m.put("showSuggestedQuestions", ch.isShowSuggestedQuestions());
        m.put("showThinking", ch.isShowThinking());
        m.put("tenantId", ch.getTenantId());
        m.put("updatedAt", ch.getUpdatedAt());
        m.put("webhookUrl", ch.getWebhookUrl());
        m.put("welcomeMessage", ch.getWelcomeMessage());
        m.put("widgetPosition", ch.getWidgetPosition());
        return m;
    }

    /** allowed_origins 列值：空 → JSON null，非空 → 数组（列表行用）。 */
    private static Object originsJson(List<String> origins) {
        return origins.isEmpty() ? null : origins;
    }

    /** 列表行（裸数组；不带 publish token）。 */
    public static List<Map<String, Object>> rows(List<EmbedChannelEntity> list) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (EmbedChannelEntity ch : list) {
            data.add(row(ch, "", false));
        }
        return data;
    }


    public static EmbedChannelEntity channel(jakarta.servlet.http.HttpServletRequest request) {
        Object ch = request.getAttribute(EmbedAuthFilter.CHANNEL_ATTRIBUTE);
        if (!(ch instanceof EmbedChannelEntity entity)) {
            throw BizException.unauthorized("unauthorized");
        }
        return entity;
    }

    /** 取当前 HTTP 请求（MockMvc/Servlet 通用）。 */
    static jakarta.servlet.http.HttpServletRequest request0() {
        var attrs = org.springframework.web.context.request.RequestContextHolder
                .currentRequestAttributes();
        return ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
    }


}
