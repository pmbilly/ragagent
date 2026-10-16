package com.ragagent.session.service.rag.spi;

import java.util.List;

import com.ragagent.session.service.rag.ChatManage;

/**
 * 知识库问答事件触发端口（引擎侧接口）：由事件清单驱动一轮知识库问答。
 *
 * <p><b>为什么在 engine</b>：入参 {@link ChatManage} 是引擎模块的会话载体，
 * L1（{@code common}）看不到它；消费方（评测域）需要的是"发起一轮 QA"，
 * 不是会话域的问答服务本身。</p>
 *
 * <p><b>实现</b>：会话域的 {@code KnowledgeQaEventAdapter} 逐字转发
 * {@code SessionKnowledgeQaService#knowledgeQAByEvent}；装配按类型注入。
 * 事件清单取 {@code PipelineBuilder.presets()} 的预置键（如 {@code "rag"}）。</p>
 */
public interface KnowledgeQaEventPort {

    /** 按事件清单发起一轮知识库问答（行为与日志同会话域实现）。 */
    void knowledgeQAByEvent(ChatManage chatManage, List<String> eventList);
}
