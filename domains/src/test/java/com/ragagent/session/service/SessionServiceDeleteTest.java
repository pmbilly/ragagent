package com.ragagent.session.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.contract.knowledge.KnowledgeCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.websearch.WebSearchTempStatePort;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.settings.ConversationProperties;
import com.ragagent.session.conversation.SessionLookupScope;
import com.ragagent.session.conversation.SessionService;
import com.ragagent.session.domain.conversation.Session;
import com.ragagent.session.repository.MessageRepository;
import com.ragagent.session.repository.MessageSuggestionRepository;
import com.ragagent.session.repository.SessionRepository;
import com.ragagent.session.support.SessionNotFoundException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 会话删除的可见性：删除必须与列表 / 详情看到的东西对齐。
 *
 * <p>回归背景：控制台列表允许管理员看到渠道托管会话（embed / IM / API-Key），
 * 但删除只按 {@code user_id = 当前用户} 过滤，导致在侧栏勾选这些会话批量删除必然
 * 404（后端日志 {@code No visible sessions found for batch delete}）。</p>
 */
class SessionServiceDeleteTest {

    private static final long TENANT = 10002L;
    private static final String OWN_ID = "sess-own";
    private static final String EMBED_ID = "sess-embed";
    private static final String USER_ID = "u-1";

    private SessionRepository repo;
    private MessageSuggestionRepository suggestionRepository;
    private SessionService service;

    @BeforeEach
    void setUp() {
        repo = mock(SessionRepository.class);
        suggestionRepository = mock(MessageSuggestionRepository.class);
        service = new SessionService(repo,
                mock(MessageRepository.class),
                suggestionRepository,
                mock(KnowledgeCatalogPort.class),
                mock(ModelCatalogPort.class),
                mock(ModelRuntime.class),
                mock(ConversationProperties.class),
                mock(WebSearchTempStatePort.class));
        TenantContext.set(TENANT, null, null, false, USER_ID, false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SessionLookupScope.clear();
    }

    private static Session session(String id, String owner) {
        Session s = new Session();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setUserId(owner);
        return s;
    }

    private void stubOwnerScopedLookup() {
        when(repo.get(eq(TENANT), anyString(), eq(OWN_ID)))
                .thenReturn(session(OWN_ID, USER_ID));
        when(repo.getImPlatform(anyLong(), anyString())).thenReturn("");
    }

    private void stubAdminConsoleEmbedLookup() {
        when(repo.get(eq(TENANT), anyString(), eq(EMBED_ID)))
                .thenThrow(new SessionNotFoundException());
        when(repo.getById(TENANT, EMBED_ID))
                .thenReturn(session(EMBED_ID, "embed_session:10002:chan-1:visitor-1"));
        when(repo.getImPlatform(anyLong(), anyString())).thenReturn("");
    }

    @Test
    void ownSessionIsDeletedInsideOwnerScope() {
        stubOwnerScopedLookup();
        when(repo.delete(eq(TENANT), nullable(String.class), eq(OWN_ID))).thenReturn(1L);

        assertThatCode(() -> service.deleteSession(OWN_ID)).doesNotThrowAnyException();
        verify(repo).delete(TENANT, USER_ID, OWN_ID);
    }

    @Test
    void adminDeletesChannelManagedSessionListedInConsole() {
        TenantContext.set(TENANT, null, "admin", false, USER_ID, false);
        stubAdminConsoleEmbedLookup();
        when(repo.delete(eq(TENANT), isNull(), eq(EMBED_ID))).thenReturn(1L);

        assertThatCode(() -> service.deleteSession(EMBED_ID)).doesNotThrowAnyException();
        verify(repo).delete(TENANT, null, EMBED_ID);
    }

    @Test
    void nonAdminStillCannotDeleteChannelManagedSession() {
        stubAdminConsoleEmbedLookup();

        assertThatThrownBy(() -> service.deleteSession(EMBED_ID))
                .isInstanceOf(SessionNotFoundException.class);
        verify(repo, never()).delete(anyLong(), any(), anyString());
    }

    @Test
    void batchDeleteAcceptsOwnAndConsoleVisibleRows() {
        TenantContext.set(TENANT, null, "admin", false, USER_ID, false);
        when(repo.getByIds(eq(TENANT), anyString(), anyList()))
                .thenReturn(List.of(session(OWN_ID, USER_ID)));
        stubAdminConsoleEmbedLookup();
        when(repo.batchDelete(anyLong(), nullable(String.class), anyList())).thenReturn(2L);

        assertThatCode(() -> service.batchDeleteSessions(List.of(OWN_ID, EMBED_ID)))
                .doesNotThrowAnyException();

        verify(repo).batchDelete(eq(TENANT), eq(USER_ID), eq(List.of(OWN_ID)));
        verify(repo).batchDelete(eq(TENANT), isNull(), eq(List.of(EMBED_ID)));
        verify(suggestionRepository).deleteBySessionIds(eq(TENANT), eq(List.of(OWN_ID, EMBED_ID)));
    }

    @Test
    void batchDeleteOfOnlyInvisibleIdsStillReportsNotFound() {
        when(repo.getByIds(eq(TENANT), anyString(), anyList())).thenReturn(List.of());
        when(repo.get(eq(TENANT), anyString(), anyString()))
                .thenThrow(new SessionNotFoundException());

        assertThatThrownBy(() -> service.batchDeleteSessions(List.of("missing-1", "missing-2")))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
