package com.yongsoo.youtubeatlasbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.api.AuthSessionResponse;
import com.yongsoo.youtubeatlasbackend.auth.api.AuthUserResponse;

class AuthServiceTest {

    private AppUserRepository appUserRepository;
    private AuthSessionRepository authSessionRepository;
    private GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;
    private GoogleTokenVerifier googleTokenVerifier;
    private AuthService authService;
    private final Map<String, AuthSession> sessionsByHash = new HashMap<>();

    @BeforeEach
    void setUp() {
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        authSessionRepository = org.mockito.Mockito.mock(AuthSessionRepository.class);
        googleAuthorizationCodeExchanger = org.mockito.Mockito.mock(GoogleAuthorizationCodeExchanger.class);
        googleTokenVerifier = org.mockito.Mockito.mock(GoogleTokenVerifier.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);
        authService = new AuthService(
            appUserRepository,
            authSessionRepository,
            googleAuthorizationCodeExchanger,
            googleTokenVerifier,
            fixedClock
        );

        when(appUserRepository.findByGoogleSubject(anyString())).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });
        when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0, AuthSession.class);
            ReflectionTestUtils.setField(session, "id", 11L);
            sessionsByHash.put(session.getTokenHash(), session);
            return session;
        });
        when(authSessionRepository.findByTokenHash(anyString())).thenAnswer(invocation ->
            Optional.ofNullable(sessionsByHash.get(invocation.getArgument(0, String.class)))
        );
    }

    @Test
    void loginWithGoogleCreatesSessionAndReturnsUser() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(
            new GoogleIdentity("google-subject-1", "atlas@example.com", "Atlas User", "https://example.com/me.png")
        );

        AuthSessionResponse response = authService.loginWithGoogle("google-id-token", 30);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-05-01T06:00:00Z"));
        assertThat(response.user().email()).isEqualTo("atlas@example.com");
        assertThat(response.user().displayName()).isEqualTo("Atlas User");
    }

    @Test
    void getCurrentUserResolvesBearerTokenToUser() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(
            new GoogleIdentity("google-subject-1", "atlas@example.com", "Atlas User", null)
        );
        AuthSessionResponse sessionResponse = authService.loginWithGoogle("google-id-token", 30);

        AuthUserResponse me = authService.getCurrentUser("Bearer " + sessionResponse.accessToken());

        assertThat(me.id()).isEqualTo(7L);
        assertThat(me.email()).isEqualTo("atlas@example.com");
        assertThat(me.displayName()).isEqualTo("Atlas User");
    }

    @Test
    void getCurrentUserRejectsExpiredSession() {
        AppUser user = new AppUser();
        user.setGoogleSubject("google-subject-1");
        user.setEmail("atlas@example.com");
        user.setDisplayName("Atlas User");
        user.setCreatedAt(Instant.parse("2026-03-01T06:00:00Z"));
        user.setLastLoginAt(Instant.parse("2026-03-31T06:00:00Z"));
        ReflectionTestUtils.setField(user, "id", 7L);

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash("expired-hash");
        session.setCreatedAt(Instant.parse("2026-03-01T06:00:00Z"));
        session.setExpiresAt(Instant.parse("2026-04-01T05:59:59Z"));

        when(authSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.getCurrentUser("Bearer expired-token"))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("만료");
    }

    @Test
    void logoutDeletesSession() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(
            new GoogleIdentity("google-subject-1", "atlas@example.com", "Atlas User", null)
        );
        AuthSessionResponse sessionResponse = authService.loginWithGoogle("google-id-token", 30);

        authService.logout("Bearer " + sessionResponse.accessToken());

        verify(authSessionRepository).delete(any(AuthSession.class));
    }

    @Test
    void requireCurrentUserReturnsAuthenticatedUser() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(
            new GoogleIdentity("google-subject-1", "atlas@example.com", "Atlas User", "https://example.com/me.png")
        );
        AuthSessionResponse sessionResponse = authService.loginWithGoogle("google-id-token", 30);

        AuthenticatedUser user = authService.requireCurrentUser("Bearer " + sessionResponse.accessToken());

        assertThat(user.id()).isEqualTo(7L);
        assertThat(user.email()).isEqualTo("atlas@example.com");
        assertThat(user.displayName()).isEqualTo("Atlas User");
    }

    @Test
    void loginWithGoogleAuthorizationCodeExchangesCodeBeforeCreatingSession() {
        when(googleAuthorizationCodeExchanger.exchangeForIdToken(
            "google-auth-code",
            "https://youtube-atlas.vercel.app",
            "https://youtube-atlas.vercel.app",
            "XmlHttpRequest"
        )).thenReturn("google-id-token");
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(
            new GoogleIdentity("google-subject-1", "atlas@example.com", "Atlas User", null)
        );

        AuthSessionResponse response = authService.loginWithGoogleAuthorizationCode(
            "google-auth-code",
            "https://youtube-atlas.vercel.app",
            "https://youtube-atlas.vercel.app",
            "XmlHttpRequest",
            30
        );

        assertThat(response.accessToken()).isNotBlank();
        verify(googleAuthorizationCodeExchanger).exchangeForIdToken(
            "google-auth-code",
            "https://youtube-atlas.vercel.app",
            "https://youtube-atlas.vercel.app",
            "XmlHttpRequest"
        );
    }
}
