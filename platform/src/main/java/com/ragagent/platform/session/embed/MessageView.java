package com.ragagent.platform.session.embed;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ragagent.platform.agent.AgentStep;
import com.ragagent.platform.retrieval.SearchResult;
import com.ragagent.contract.session.MentionedItem;
import com.ragagent.contract.session.UsedMemory;
import com.ragagent.contract.session.MessageArtifact;
import com.ragagent.contract.session.MessageAttachment;
import com.ragagent.contract.session.MessageExecutionContext;
import com.ragagent.contract.session.MessageImage;
import com.ragagent.contract.model.TokenUsage;

/**
 * 消息行**线格式视图**（embed 公开面 messages/load 响应形状与会话 QA 面载荷）。
 *
 * <p><b>为什么有它</b>：实体 Message 属会话域内部（HANDOFF §12.0 判据 1），而
 * EmbedSessionPort#loadMessages 需把它作为响应体、SessionQaPort 需经它落库。
 * 本视图**逐一镜像实体的 26 个字段与默认值**，并在同样的三个字段上标 @JsonIgnore
 * （renderedContent / agentTenantId / executionContext）⇒ **JSON 逐字节等价**
 * （由 emb-pub-messages-load*.json 金片钉住）。</p>
 *
 * <p><b>可写说明</b>：DTO——适配器填充、装配面构造；访问器名与实体一致（消费方零改动）。</p>
 */
public class MessageView {
    private String id = "";

    private String sessionId = "";

    private String requestId = "";

    private String content = "";

    private String role = "";

    private List<SearchResult> knowledgeReferences = new ArrayList<>();

    private List<AgentStep> agentSteps = new ArrayList<>();

    private List<MentionedItem> mentionedItems = new ArrayList<>();

    private List<MessageImage> images = new ArrayList<>();

    private List<MessageAttachment> attachments = new ArrayList<>();

    private List<MessageArtifact> artifacts = new ArrayList<>();

    private boolean completed;

    private boolean fallback;

    private long agentDurationMs;

    private TokenUsage usage;

    @JsonIgnore
    private String renderedContent = "";

    private String channel;

    private String agentId;

    @JsonIgnore
    private long agentTenantId;

    private String modelId;

    @JsonIgnore
    private MessageExecutionContext executionContext;

    private String knowledgeId;

    private List<UsedMemory> usedMemories;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<SearchResult> getKnowledgeReferences() {
        return knowledgeReferences;
    }

    public void setKnowledgeReferences(List<SearchResult> knowledgeReferences) {
        this.knowledgeReferences = knowledgeReferences;
    }

    public List<AgentStep> getAgentSteps() {
        return agentSteps;
    }

    public void setAgentSteps(List<AgentStep> agentSteps) {
        this.agentSteps = agentSteps;
    }

    public List<MentionedItem> getMentionedItems() {
        return mentionedItems;
    }

    public void setMentionedItems(List<MentionedItem> mentionedItems) {
        this.mentionedItems = mentionedItems;
    }

    public List<MessageImage> getImages() {
        return images;
    }

    public void setImages(List<MessageImage> images) {
        this.images = images;
    }

    public List<MessageAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<MessageAttachment> attachments) {
        this.attachments = attachments;
    }

    public List<MessageArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<MessageArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public long getAgentDurationMs() {
        return agentDurationMs;
    }

    public void setAgentDurationMs(long agentDurationMs) {
        this.agentDurationMs = agentDurationMs;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public void setUsage(TokenUsage usage) {
        this.usage = usage;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public void setRenderedContent(String renderedContent) {
        this.renderedContent = renderedContent;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public long getAgentTenantId() {
        return agentTenantId;
    }

    public void setAgentTenantId(long agentTenantId) {
        this.agentTenantId = agentTenantId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public MessageExecutionContext getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(MessageExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public String getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public List<UsedMemory> getUsedMemories() {
        return usedMemories;
    }

    public void setUsedMemories(List<UsedMemory> usedMemories) {
        this.usedMemories = usedMemories;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
