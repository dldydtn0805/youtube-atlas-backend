package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

class AdminAccessServiceTest {

    private AuthService authService;
    private AtlasProperties atlasProperties;
    private AdminAccessService adminAccessService;

    @BeforeEach
    void setUp() {
        authService = org.mockito.Mockito.mock(AuthService.class);
        atlasProperties = new AtlasProperties();
        adminAccessService = new AdminAccessService(authService, atlasProperties);
    }

    @Test
    void requireAdminReturnsUserWhenEmailIsAllowed() {
        atlasProperties.getAdmin().setAllowedEmails(List.of("admin@example.com"));
        AuthenticatedUser user = new AuthenticatedUser(7L, "Admin@example.com", "Admin", null);
        when(authService.requireCurrentUser("Bearer token")).thenReturn(user);

        AuthenticatedUser result = adminAccessService.requireAdmin("Bearer token");

        assertThat(result).isEqualTo(user);
    }

    @Test
    void requireAdminRejectsUserWhenEmailIsNotAllowed() {
        atlasProperties.getAdmin().setAllowedEmails(List.of("owner@example.com"));
        when(authService.requireCurrentUser("Bearer token")).thenReturn(
            new AuthenticatedUser(9L, "viewer@example.com", "Viewer", null)
        );

        assertThatThrownBy(() -> adminAccessService.requireAdmin("Bearer token"))
            .isInstanceOf(AdminException.class)
            .hasMessageContaining("관리자 권한");
    }
}
