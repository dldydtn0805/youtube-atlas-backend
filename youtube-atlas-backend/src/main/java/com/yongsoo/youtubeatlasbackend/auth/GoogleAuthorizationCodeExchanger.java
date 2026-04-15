package com.yongsoo.youtubeatlasbackend.auth;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yongsoo.youtubeatlasbackend.common.ExternalServiceException;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

@Component
public class GoogleAuthorizationCodeExchanger {

    private static final String XML_HTTP_REQUEST = "XmlHttpRequest";
    private static final Logger log = LoggerFactory.getLogger(GoogleAuthorizationCodeExchanger.class);

    private final AtlasProperties atlasProperties;
    private final RestTemplate restTemplate;

    public GoogleAuthorizationCodeExchanger(
        AtlasProperties atlasProperties,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this.atlasProperties = atlasProperties;
        this.restTemplate = restTemplateBuilder.build();
    }

    public String exchangeForIdToken(String code, String redirectUri, String origin, String requestedWith) {
        if (!StringUtils.hasText(code)) {
            log.warn("Google OAuth code exchange rejected: missing authorization code");
            throw new IllegalArgumentException("code는 필수입니다.");
        }

        String normalizedRedirectUri = normalizeOrigin(redirectUri, "redirectUri");
        String normalizedOrigin = normalizeOrigin(origin, "Origin 헤더");
        String normalizedRequestedWith = StringUtils.hasText(requestedWith) ? requestedWith.trim() : "";

        if (!StringUtils.hasText(requestedWith) || !XML_HTTP_REQUEST.equalsIgnoreCase(normalizedRequestedWith)) {
            log.warn(
                "Google OAuth code exchange rejected: invalid X-Requested-With. origin={}, redirectUri={}, requestedWith={}",
                normalizedOrigin,
                normalizedRedirectUri,
                normalizedRequestedWith
            );
            throw new AuthException("invalid_google_oauth_request", "Google OAuth 요청을 확인할 수 없습니다.");
        }

        if (!normalizedRedirectUri.equals(normalizedOrigin)) {
            log.warn(
                "Google OAuth code exchange rejected: origin mismatch. origin={}, redirectUri={}",
                normalizedOrigin,
                normalizedRedirectUri
            );
            throw new AuthException("invalid_google_oauth_request", "redirectUri와 Origin이 일치하지 않습니다.");
        }

        if (!PatternMatchUtils.simpleMatch(
            atlasProperties.getRealtime().getAllowedOrigins().toArray(String[]::new),
            normalizedOrigin
        )) {
            log.warn(
                "Google OAuth code exchange rejected: origin not allowed. origin={}, allowedOrigins={}",
                normalizedOrigin,
                atlasProperties.getRealtime().getAllowedOrigins()
            );
            throw new AuthException("invalid_google_oauth_request", "허용되지 않은 로그인 요청 출처입니다.");
        }

        log.info(
            "Google OAuth code exchange started. origin={}, redirectUri={}",
            normalizedOrigin,
            normalizedRedirectUri
        );
        return exchangeAuthorizationCode(code.trim(), normalizedRedirectUri);
    }

    private String exchangeAuthorizationCode(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", requireGoogleClientId());
        form.add("client_secret", requireGoogleClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                requireTokenUrl(),
                new HttpEntity<>(form, headers),
                TokenResponse.class
            );
            TokenResponse body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || body == null || !StringUtils.hasText(body.idToken())) {
                log.error(
                    "Google OAuth code exchange failed: token endpoint returned invalid response. status={}",
                    response.getStatusCode()
                );
                throw new ExternalServiceException("Google 승인 코드를 세션 토큰으로 교환하지 못했습니다.");
            }

            log.info("Google OAuth code exchange succeeded");
            return body.idToken().trim();
        } catch (HttpClientErrorException exception) {
            log.warn(
                "Google OAuth code exchange failed: invalid or expired code. status={}, responseBody={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            throw new AuthException("invalid_google_code", "구글 로그인 승인 코드가 유효하지 않거나 만료되었습니다.");
        } catch (RestClientException exception) {
            log.error("Google OAuth code exchange failed: token endpoint request error", exception);
            throw new ExternalServiceException("Google 승인 코드를 세션 토큰으로 교환하지 못했습니다.", exception);
        }
    }

    private String normalizeOrigin(String candidate, String fieldName) {
        if (!StringUtils.hasText(candidate)) {
            log.warn("Google OAuth code exchange rejected: missing {}", fieldName);
            throw new AuthException("invalid_google_oauth_request", fieldName + "를 확인할 수 없습니다.");
        }

        try {
            URI uri = URI.create(candidate.trim());
            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost();

            if ((!scheme.equals("http") && !scheme.equals("https")) || !StringUtils.hasText(host)) {
                throw new IllegalArgumentException("invalid_origin");
            }

            String authority = uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
            return scheme + "://" + authority;
        } catch (IllegalArgumentException exception) {
            log.warn("Google OAuth code exchange rejected: invalid {} format. value={}", fieldName, candidate);
            throw new AuthException("invalid_google_oauth_request", fieldName + " 형식이 올바르지 않습니다.");
        }
    }

    private String requireGoogleClientId() {
        String clientId = atlasProperties.getAuth().getGoogleClientId();

        if (!StringUtils.hasText(clientId)) {
            throw new IllegalArgumentException("GOOGLE_CLIENT_ID 환경 변수가 설정되지 않았습니다.");
        }

        return clientId.trim();
    }

    private String requireGoogleClientSecret() {
        String clientSecret = atlasProperties.getAuth().getGoogleClientSecret();

        if (!StringUtils.hasText(clientSecret)) {
            throw new IllegalArgumentException("GOOGLE_CLIENT_SECRET 환경 변수가 설정되지 않았습니다.");
        }

        return clientSecret.trim();
    }

    private String requireTokenUrl() {
        String tokenUrl = atlasProperties.getAuth().getGoogleTokenUrl();

        if (!StringUtils.hasText(tokenUrl)) {
            throw new IllegalArgumentException("GOOGLE_TOKEN_URL 환경 변수가 설정되지 않았습니다.");
        }

        return tokenUrl.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
        @JsonProperty("id_token") String idToken
    ) {
    }
}
