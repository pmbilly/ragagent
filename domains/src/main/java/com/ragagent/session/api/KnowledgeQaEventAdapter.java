package com.ragagent.session.api;

import com.ragagent.session.rag.ChatManage;
import com.ragagent.session.rag.PipelineEventType;
import com.ragagent.session.rag.spi.KnowledgeQaEventPort;
import com.ragagent.session.service.SessionKnowledgeQaService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeQaEventPort} 的会话域实现：逐字转发
 * {@link SessionKnowledgeQaService#knowledgeQAByEvent}，不新增缓冲、不改日志口径。
 */
@Component
public class KnowledgeQaEventAdapter implements KnowledgeQaEventPort {

    private final SessionKnowledgeQaService knowledgeQaService;

    public KnowledgeQaEventAdapter(SessionKnowledgeQaService knowledgeQaService) {
        this.knowledgeQaService = knowledgeQaService;
    }

    @Override
    public void knowledgeQAByEvent(ChatManage chatManage, List<PipelineEventType> eventList) {
        knowledgeQaService.knowledgeQAByEvent(chatManage, eventList);
    }
}
