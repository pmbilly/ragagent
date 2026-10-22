package com.ragagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.TenantRowView;
import com.ragagent.auth.repository.UserRepository;
import com.ragagent.contract.auth.UserPreferences;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginTenantResolverTest {

    private static final String USER_ID = "user-1";

    private final UserRepository userRepo = mock(UserRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final TenantMemberService memberService = mock(TenantMemberService.class);
    private final LoginTenantResolver resolver =
            new LoginTenantResolver(userRepo, tenantService, memberService);

    @BeforeEach
    void setUp() {
        when(tenantService.getTenantById(anyLong())).thenReturn(null);
        when(memberService.listByUser(anyString())).thenReturn(List.of());
    }

    @Test
    void preferredTenantWinsForGlobalUser() {
        User user = user(1L, 2L, true);
        when(tenantService.getTenantById(2L)).thenReturn(tenant(2L));

        assertThat(resolver.resolve(user)).isEqualTo(2L);
    }

    @Test
    void stalePreferenceIsClearedAndFallsBackToHome() {
        User user = user(1L, 9L, true);

        assertThat(resolver.resolve(user)).isEqualTo(1L);
        assertThat(user.getPreferences().getLastActiveTenantId()).isNull();
        verify(userRepo).updateById(user);
    }

    @Test
    void homeWithoutMembershipFallsBackToFirstActiveMembership() {
        User user = user(1L, null, false);
        when(memberService.getMembership(USER_ID, 1L)).thenReturn(member(1L, "removed"));
        when(memberService.listByUser(USER_ID)).thenReturn(List.of(member(2L, "active")));
        when(tenantService.getTenantById(2L)).thenReturn(tenant(2L));

        assertThat(resolver.resolve(user)).isEqualTo(2L);
        verify(userRepo).clearTenantId(USER_ID);
        assertThat(user.getTenantId()).isEqualTo(2L);
        verify(userRepo).updateById(user);
    }

    private static User user(long home, Long preferred, boolean canAccessAll) {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(home);
        user.setCanAccessAllTenants(canAccessAll);
        UserPreferences prefs = new UserPreferences();
        prefs.setLastActiveTenantId(preferred);
        user.setPreferences(prefs);
        return user;
    }

    private static TenantMember member(long tenantId, String status) {
        TenantMember member = new TenantMember();
        member.setTenantId(tenantId);
        member.setStatus(status);
        return member;
    }

    private static TenantRowView tenant(long id) {
        TenantRowView view = new TenantRowView();
        view.setId(id);
        view.setName("tenant-" + id);
        return view;
    }
}
