package com.ragagent.session.dto;

import com.ragagent.contract.session.SessionView;
import com.ragagent.session.domain.conversation.Session;

/** 会话实体 → 契约线格式视图。 */
public final class SessionViewMapper {

    private SessionViewMapper() {
    }

    public static SessionView from(Session session) {
        SessionView view = new SessionView();
        view.setId(session.getId());
        view.setTitle(session.getTitle());
        view.setDescription(session.getDescription());
        view.setTenantId(session.getTenantId());
        view.setUserId(session.getUserId());
        view.setEmbedVisitorId(session.embedVisitorIdValue());
        view.setPinned(session.isPinned());
        view.setPinnedAt(session.getPinnedAt());
        view.setLastRequestState(session.getLastRequestState());
        view.setCreatedAt(session.getCreatedAt());
        view.setUpdatedAt(session.getUpdatedAt());
        view.setDeletedAt(session.getDeletedAt());
        view.setImPlatform(session.getImPlatform());
        return view;
    }
}
