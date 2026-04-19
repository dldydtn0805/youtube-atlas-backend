package com.yongsoo.youtubeatlasbackend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.api.AuthSessionResponse;
import com.yongsoo.youtubeatlasbackend.auth.api.AuthUserResponse;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressService;
import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PlaybackProgressService playbackProgressService;
    private final CommentService commentService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger,
        GoogleTokenVerifier googleTokenVerifier,
        PlaybackProgressService playbackProgressService,
        CommentService commentService,
        Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.googleAuthorizationCodeExchanger = googleAuthorizationCodeExchanger;
        this.googleTokenVerifier = googleTokenVerifier;
        this.playbackProgressService = playbackProgressService;
        this.commentService = commentService;
        this.clock = clock;
    }

    @Transactional
    public AuthSessionResponse loginWithGoogle(String idToken, int sessionTtlDays) {
        if (sessionTtlDays <= 0) {
            throw new IllegalArgumentException("AUTH_SESSION_TTL_DAYS는 1 이상이어야 합니다.");
        }

        GoogleIdentity identity = googleTokenVerifier.verify(idToken);
        Instant now = Instant.now(clock);

        AppUser user = appUserRepository.findByGoogleSubject(identity.subject())
            .orElseGet(AppUser::new);

        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }

        user.setGoogleSubject(identity.subject());
        user.setEmail(identity.email());
        user.setDisplayName(identity.name());
        user.setPictureUrl(identity.pictureUrl());
        user.setLastLoginAt(now);

        AppUser savedUser = appUserRepository.save(user);
        String rawToken = generateToken();

        AuthSession session = new AuthSession();
        session.setUser(savedUser);
        session.setTokenHash(hashToken(rawToken));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(sessionTtlDays * 24L * 60L * 60L));
        authSessionRepository.save(session);
        publishLoginSystemMessage(savedUser);

        return new AuthSessionResponse(
            rawToken,
            "Bearer",
            session.getExpiresAt(),
            toUserResponse(savedUser)
        );
    }

    @Transactional
    public AuthSessionResponse loginWithGoogleAuthorizationCode(
        String code,
        String redirectUri,
        String origin,
        String requestedWith,
        int sessionTtlDays
    ) {
        String idToken = googleAuthorizationCodeExchanger.exchangeForIdToken(code, redirectUri, origin, requestedWith);
        return loginWithGoogle(idToken, sessionTtlDays);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(String authorizationHeader) {
        return toUserResponse(requireSession(authorizationHeader).getUser());
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser getCurrentUserOrNull(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }

        return toAuthenticatedUser(requireSession(authorizationHeader).getUser());
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser requireCurrentUser(String authorizationHeader) {
        return toAuthenticatedUser(requireSession(authorizationHeader).getUser());
    }

    @Transactional
    public void logout(String authorizationHeader) {
        authSessionRepository.delete(requireSession(authorizationHeader));
    }

    private AuthSession requireSession(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        String tokenHash = hashToken(token);
        Instant now = Instant.now(clock);

        AuthSession session = authSessionRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthException("unauthorized", "로그인이 필요합니다."));

        if (session.getExpiresAt().isBefore(now) || session.getExpiresAt().equals(now)) {
            throw new AuthException("session_expired", "로그인 세션이 만료되었습니다.");
        }

        return session;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            throw new AuthException("unauthorized", "로그인이 필요합니다.");
        }

        String trimmedHeader = authorizationHeader.trim();
        String prefix = "Bearer ";

        if (!trimmedHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new AuthException("unauthorized", "Authorization 헤더는 Bearer 토큰 형식이어야 합니다.");
        }

        String token = trimmedHeader.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AuthException("unauthorized", "Authorization 토큰이 비어 있습니다.");
        }

        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void publishLoginSystemMessage(AppUser user) {
        String displayName = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName().trim() : "익명";
        commentService.publishLoginSystemMessage(displayName + "님이 로그인했습니다.");
    }

    private String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private AuthUserResponse toUserResponse(AppUser user) {
        PlaybackProgressResponse lastPlaybackProgress = playbackProgressService.getCurrentProgressForUserId(user.getId())
            .orElse(null);

        return new AuthUserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getPictureUrl(),
            user.getLastLoginAt(),
            lastPlaybackProgress
        );
    }

    private AuthenticatedUser toAuthenticatedUser(AppUser user) {
        return new AuthenticatedUser(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getPictureUrl()
        );
    }
}
