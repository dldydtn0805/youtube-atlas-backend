package com.yongsoo.youtubeatlasbackend.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yongsoo.youtubeatlasbackend.common.ExternalServiceException;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

@Component
public class GoogleTokenVerifier {

    private static final Set<String> TRUSTED_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AtlasProperties atlasProperties;
    private final RestTemplate restTemplate;
    private final Clock clock;

    public GoogleTokenVerifier(
        AtlasProperties atlasProperties,
        RestTemplateBuilder restTemplateBuilder,
        Clock clock
    ) {
        this.atlasProperties = atlasProperties;
        this.restTemplate = restTemplateBuilder.build();
        this.clock = clock;
    }

    public GoogleIdentity verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("idToken은 필수입니다.");
        }

        String googleClientId = requireGoogleClientId();
        String url = UriComponentsBuilder.fromHttpUrl(requireTokenInfoUrl())
            .queryParam("id_token", idToken.trim())
            .toUriString();

        TokenInfoResponse tokenInfo = fetchTokenInfo(url);

        if (!googleClientId.equals(tokenInfo.audience())) {
            throw new AuthException("invalid_google_token", "이 앱에서 발급된 구글 로그인 토큰이 아닙니다.");
        }

        if (!TRUSTED_ISSUERS.contains(tokenInfo.issuer())) {
            throw new AuthException("invalid_google_token", "신뢰할 수 없는 구글 로그인 토큰입니다.");
        }

        if (!StringUtils.hasText(tokenInfo.subject()) || !StringUtils.hasText(tokenInfo.email())) {
            throw new AuthException("invalid_google_token", "구글 계정 정보를 확인할 수 없습니다.");
        }

        if (!tokenInfo.isEmailVerified()) {
            throw new AuthException("invalid_google_token", "이메일 인증이 완료된 구글 계정만 로그인할 수 있습니다.");
        }

        if (tokenInfo.parseExpiresAt() <= Instant.now(clock).getEpochSecond()) {
            throw new AuthException("invalid_google_token", "만료된 구글 로그인 토큰입니다.");
        }

        String name = StringUtils.hasText(tokenInfo.name()) ? tokenInfo.name().trim() : tokenInfo.email().trim();
        String pictureUrl = StringUtils.hasText(tokenInfo.picture()) ? tokenInfo.picture().trim() : null;

        return new GoogleIdentity(
            tokenInfo.subject().trim(),
            tokenInfo.email().trim(),
            name,
            pictureUrl
        );
    }

    private TokenInfoResponse fetchTokenInfo(String url) {
        try {
            ResponseEntity<TokenInfoResponse> response = restTemplate.getForEntity(url, TokenInfoResponse.class);
            TokenInfoResponse body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new ExternalServiceException("Google 로그인 토큰 검증에 실패했습니다.");
            }

            return body;
        } catch (HttpClientErrorException exception) {
            throw new AuthException("invalid_google_token", "유효하지 않은 구글 로그인 토큰입니다.");
        } catch (RestClientException exception) {
            throw new ExternalServiceException("Google 로그인 토큰 검증에 실패했습니다.", exception);
        }
    }

    private String requireGoogleClientId() {
        String googleClientId = atlasProperties.getAuth().getGoogleClientId();

        if (!StringUtils.hasText(googleClientId)) {
            throw new IllegalArgumentException("GOOGLE_CLIENT_ID 환경 변수가 설정되지 않았습니다.");
        }

        return googleClientId.trim();
    }

    private String requireTokenInfoUrl() {
        String tokenInfoUrl = atlasProperties.getAuth().getGoogleTokenInfoUrl();

        if (!StringUtils.hasText(tokenInfoUrl)) {
            throw new IllegalArgumentException("GOOGLE_TOKEN_INFO_URL 환경 변수가 설정되지 않았습니다.");
        }

        return tokenInfoUrl.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenInfoResponse(
        @JsonProperty("sub") String subject,
        @JsonProperty("aud") String audience,
        @JsonProperty("iss") String issuer,
        String email,
        @JsonProperty("email_verified") String emailVerified,
        @JsonProperty("exp") String expiresAt,
        String name,
        String picture
    ) {
        boolean isEmailVerified() {
            return "true".equalsIgnoreCase(emailVerified);
        }

        long parseExpiresAt() {
            try {
                return StringUtils.hasText(expiresAt) ? Long.parseLong(expiresAt) : 0L;
            } catch (NumberFormatException exception) {
                throw new AuthException("invalid_google_token", "구글 로그인 토큰 만료 시간을 확인할 수 없습니다.");
            }
        }
    }
}
