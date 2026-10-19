package com.ragagent.session.dto;

import com.ragagent.contract.session.MessageView;
import com.ragagent.session.domain.message.Message;

/** 消息实体 → 契约线格式视图。 */
public final class MessageViewMapper {

    private MessageViewMapper() {
    }

    public static MessageView from(Message message) {
        if (message == null) {
            return null;
        }
        MessageView view = new MessageView();
        view.setId(message.getId());
        view.setSessionId(message.getSessionId());
        view.setRequestId(message.getRequestId());
        view.setContent(message.getContent());
        view.setRole(message.getRole());
        view.setKnowledgeReferences(message.getKnowledgeReferences());
        view.setAgentSteps(message.getAgentSteps());
        view.setMentionedItems(message.getMentionedItems());
        view.setImages(message.getImages());
        view.setAttachments(message.getAttachments());
        view.setArtifacts(message.getArtifacts());
        view.setCompleted(message.isCompleted());
        view.setFallback(message.isFallback());
        view.setAgentDurationMs(message.getAgentDurationMs());
        view.setUsage(message.getUsage());
        view.setRenderedContent(message.getRenderedContent());
        view.setChannel(message.getChannel());
        view.setAgentId(message.getAgentId());
        view.setAgentTenantId(message.getAgentTenantId());
        view.setModelId(message.getModelId());
        view.setExecutionContext(message.getExecutionContext());
        view.setKnowledgeId(message.getKnowledgeId());
        view.setUsedMemories(message.getUsedMemories());
        view.setCreatedAt(message.getCreatedAt());
        view.setUpdatedAt(message.getUpdatedAt());
        view.setDeletedAt(message.getDeletedAt());
        return view;
    }
}
