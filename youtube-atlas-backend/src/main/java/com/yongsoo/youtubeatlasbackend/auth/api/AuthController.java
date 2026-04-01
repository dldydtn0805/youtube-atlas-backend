package com.yongsoo.youtubeatlasbackend.auth.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AtlasProperties atlasProperties;

    public AuthController(AuthService authService, AtlasProperties atlasProperties) {
        this.authService = authService;
        this.atlasProperties = atlasProperties;
    }

    @PostMapping("/google")
    public AuthSessionResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken(), atlasProperties.getAuth().getSessionTtlDays());
    }

    @GetMapping("/google/config")
    public GoogleAuthConfigResponse getGoogleAuthConfig() {
        String clientId = atlasProperties.getAuth().getGoogleClientId();
        String normalizedClientId = StringUtils.hasText(clientId) ? clientId.trim() : "";

        return new GoogleAuthConfigResponse(
            normalizedClientId,
            StringUtils.hasText(normalizedClientId)
        );
    }

    @GetMapping("/me")
    public AuthUserResponse getCurrentUser(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.getCurrentUser(authorizationHeader);
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
    }
}
