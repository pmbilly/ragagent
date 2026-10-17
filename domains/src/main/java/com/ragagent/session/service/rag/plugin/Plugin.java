package com.ragagent.session.service.rag.plugin;

import com.ragagent.session.service.rag.ChatManage;
import com.ragagent.session.service.rag.PipelineEventType;

/**
 * chat 管线插件接口。
 *
 * <p>插件处理特定事件；{@code next} 是插件链的下一环，由 {@link Chain}
 * 函数式接口表达。返回 {@code null} 表示无错误。</p>
 */
public interface Plugin {

    /** 处理事件。返回 null = 无错误。 */
    PluginError onEvent(PipelineEventType eventType, ChatManage chatManage, Chain next);

    /** 本插件响应的事件类型列表。 */
    PipelineEventType[] activationEvents();

    /** 插件链的下一环。 */
    @FunctionalInterface
    interface Chain {
        PluginError next();
    }
}
