package com.ragagent.session.assembler;

import com.ragagent.session.controller.vo.ArtifactRespVo;
import com.ragagent.session.controller.vo.ChatHistoryKbStatsRespVo;
import com.ragagent.session.controller.vo.GenerateTitleRespVo;
import com.ragagent.session.controller.vo.MessageSearchGroupItemVo;
import com.ragagent.session.controller.vo.MessageSearchRespVo;
import com.ragagent.session.controller.vo.MessageSuggestionRespVo;
import com.ragagent.session.controller.vo.SessionListItemVo;
import com.ragagent.session.controller.vo.SessionListRespVo;
import com.ragagent.session.controller.vo.SessionPinRespVo;
import com.ragagent.session.controller.vo.TemporaryDocumentRespVo;
import com.ragagent.session.dto.ArtifactView;
import com.ragagent.session.dto.ChatHistoryKbStats;
import com.ragagent.session.dto.MessageSearchGroupItem;
import com.ragagent.session.dto.MessageSearchResult;
import com.ragagent.session.dto.MessageSuggestionResponse;
import com.ragagent.session.dto.SessionListItem;
import com.ragagent.session.dto.SessionListResponse;
import com.ragagent.session.dto.TemporaryDocumentResponse;
import java.util.List;

/** 会话域响应 DTO 到 HTTP VO 的装配。 */
public final class SessionAssembler {

    private SessionAssembler() {
    }

    /** 装配产物元数据。 */
    public static ArtifactRespVo toArtifactVo(ArtifactView src) {
        if (src == null) {
            return null;
        }
        return new ArtifactRespVo(
                src.index(),
                src.handle(),
                src.fileName(),
                src.fileType(),
                src.fileSize(),
                src.sourcePath(),
                src.modTime(),
                src.createdAt());
    }

    /** 批量装配产物元数据，保持原列表下标顺序。 */
    public static List<ArtifactRespVo> toArtifactVoList(List<ArtifactView> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(SessionAssembler::toArtifactVo).toList();
    }

    /** 装配聊天历史知识库统计。 */
    public static ChatHistoryKbStatsRespVo toChatHistoryKbStatsVo(ChatHistoryKbStats src) {
        if (src == null) {
            return null;
        }
        return new ChatHistoryKbStatsRespVo(
                src.isEnabled(),
                src.getEmbeddingModelId(),
                src.getKnowledgeBaseId(),
                src.getKnowledgeBaseName(),
                src.getIndexedMessageCount(),
                src.isHasIndexedMessages());
    }

    /** 装配消息搜索响应；空结果的 items 是空数组而非 null。 */
    public static MessageSearchRespVo toMessageSearchVo(MessageSearchResult src) {
        if (src == null) {
            return null;
        }
        List<MessageSearchGroupItem> items = src.getItems();
        List<MessageSearchGroupItemVo> voItems = items == null
                ? null
                : items.stream().map(SessionAssembler::toSearchItemVo).toList();
        return new MessageSearchRespVo(voItems, src.getTotal());
    }

    /** 装配消息建议集合。 */
    public static MessageSuggestionRespVo toSuggestionVo(MessageSuggestionResponse src) {
        if (src == null) {
            return null;
        }
        return new MessageSuggestionRespVo(
                src.id(),
                src.tenantId(),
                src.sessionId(),
                src.assistantMessageId(),
                src.agentId(),
                src.placement(),
                src.configHash(),
                src.locale(),
                src.status(),
                src.allowRegenerate(),
                src.suppressionReason(),
                src.questions(),
                src.modelId(),
                src.promptTokens(),
                src.completionTokens(),
                src.latencyMs(),
                src.errorCode(),
                src.generatedAt(),
                src.createdAt(),
                src.updatedAt());
    }

    /** 装配会话分页响应。 */
    public static SessionListRespVo toSessionListVo(SessionListResponse src) {
        if (src == null) {
            return null;
        }
        List<SessionListItem> items = src.items();
        List<SessionListItemVo> voItems = items == null
                ? null
                : items.stream().map(SessionAssembler::toSessionItemVo).toList();
        return new SessionListRespVo(voItems, src.page(), src.pageSize(), src.total());
    }

    /** 装配置顶状态。 */
    public static SessionPinRespVo toPinVo(boolean pinned) {
        return new SessionPinRespVo(pinned);
    }

    /** 装配生成标题响应。 */
    public static GenerateTitleRespVo toGenerateTitleVo(String title) {
        return new GenerateTitleRespVo(title);
    }

    /** 装配会话附件。 */
    public static TemporaryDocumentRespVo toTemporaryDocumentVo(TemporaryDocumentResponse src) {
        if (src == null) {
            return null;
        }
        return new TemporaryDocumentRespVo(
                src.id(),
                src.tenantId(),
                src.sessionId(),
                src.fileName(),
                src.fileType(),
                src.mimeType(),
                src.fileSize(),
                src.status(),
                src.imageRefs(),
                src.metadata(),
                src.tokenCount(),
                src.chunkCount(),
                src.errorMessage(),
                src.expiresAt(),
                src.startedAt(),
                src.readyAt(),
                src.createdAt(),
                src.updatedAt());
    }

    /** 批量装配会话附件，保持原有顺序。 */
    public static List<TemporaryDocumentRespVo> toTemporaryDocumentVoList(List<TemporaryDocumentResponse> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(SessionAssembler::toTemporaryDocumentVo).toList();
    }

    private static MessageSearchGroupItemVo toSearchItemVo(MessageSearchGroupItem src) {
        if (src == null) {
            return null;
        }
        return new MessageSearchGroupItemVo(
                src.getRequestId(),
                src.getSessionId(),
                src.getSessionTitle(),
                src.getQueryContent(),
                src.getAnswerContent(),
                src.getScore(),
                src.getMatchType(),
                src.getCreatedAt());
    }

    private static SessionListItemVo toSessionItemVo(SessionListItem src) {
        if (src == null) {
            return null;
        }
        return new SessionListItemVo(
                src.getId(),
                src.getTitle(),
                src.getDescription(),
                src.getTenantId(),
                src.getUserId(),
                src.isPinned(),
                src.getPinnedAt(),
                src.getLastRequestState(),
                src.getCreatedAt(),
                src.getUpdatedAt(),
                src.getDeletedAt(),
                src.getImPlatform(),
                src.getImChatId(),
                src.getImThreadId(),
                src.getImUserId(),
                src.getImAgentId(),
                src.getImChannelId());
    }
}
