package com.yongsoo.youtubeatlasbackend.admin;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

@Service
public class AdminAccessService {

    private final AuthService authService;
    private final AtlasProperties atlasProperties;

    public AdminAccessService(AuthService authService, AtlasProperties atlasProperties) {
        this.authService = authService;
        this.atlasProperties = atlasProperties;
    }

    public AuthenticatedUser requireAdmin(String authorizationHeader) {
        AuthenticatedUser user = authService.requireCurrentUser(authorizationHeader);
        Set<String> allowedEmails = atlasProperties.getAdmin().getAllowedEmails().stream()
            .filter(StringUtils::hasText)
            .map(email -> email.trim().toLowerCase())
            .collect(Collectors.toUnmodifiableSet());

        if (!allowedEmails.contains(user.email().trim().toLowerCase())) {
            throw new AdminException("forbidden", "관리자 권한이 필요합니다.");
        }

        return user;
    }
}
