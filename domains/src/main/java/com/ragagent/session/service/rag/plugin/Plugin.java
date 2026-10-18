package com.ragagent.session.service.rag.plugin;

import com.ragagent.session.service.rag.ChatManage;
import com.ragagent.session.service.rag.PipelineEventType;

/**
 * chat 管线插件接口。
 *
 * <h2>三条契约</h2>
 * <ul>
 *   <li><b>读</b>：从 {@link ChatManage} 取上游写入的字段——不要假设未写入的字段有值。</li>
 *   <li><b>写</b>：把本段结果写回 {@link ChatManage}；字段名就是下游可见的数据键。</li>
 *   <li><b>失败</b>：返回 {@link PluginError} 终止整条链（后续插件与事件都不再跑）；
 *       返回 {@code null} 表示继续。{@code next.next()} 决定是否把控制权交给链的下一环。</li>
 * </ul>
 *
 * <p>链的顺序由注册顺序决定（运行期），静态调用图看不到——事件、插件与读写字段的
 * 对照表由 {@code docs/architecture/pipeline-map.md} 生成维护。</p>
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
