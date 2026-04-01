package com.yongsoo.youtubeatlasbackend.favorites;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.favorites.api.CreateFavoriteStreamerRequest;
import com.yongsoo.youtubeatlasbackend.favorites.api.FavoriteStreamerResponse;

@Service
public class FavoriteStreamerService {

    private final FavoriteStreamerRepository favoriteStreamerRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public FavoriteStreamerService(
        FavoriteStreamerRepository favoriteStreamerRepository,
        AppUserRepository appUserRepository,
        Clock clock
    ) {
        this.favoriteStreamerRepository = favoriteStreamerRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FavoriteStreamerResponse> getFavoriteStreamers(AuthenticatedUser authenticatedUser) {
        return favoriteStreamerRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUser.id()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public FavoriteStreamerResponse addFavoriteStreamer(
        AuthenticatedUser authenticatedUser,
        CreateFavoriteStreamerRequest request
    ) {
        String channelId = normalizeRequired(request.channelId(), "channelId는 필수입니다.");
        String channelTitle = normalizeRequired(request.channelTitle(), "channelTitle은 필수입니다.");
        String thumbnailUrl = normalizeOptional(request.thumbnailUrl());

        FavoriteStreamer favoriteStreamer = favoriteStreamerRepository.findByUserIdAndChannelId(authenticatedUser.id(), channelId)
            .orElseGet(FavoriteStreamer::new);

        if (favoriteStreamer.getCreatedAt() == null) {
            AppUser user = appUserRepository.findById(authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
            favoriteStreamer.setUser(user);
            favoriteStreamer.setCreatedAt(Instant.now(clock));
        }

        favoriteStreamer.setChannelId(channelId);
        favoriteStreamer.setChannelTitle(channelTitle);
        favoriteStreamer.setThumbnailUrl(thumbnailUrl);

        return toResponse(favoriteStreamerRepository.save(favoriteStreamer));
    }

    @Transactional
    public void deleteFavoriteStreamer(AuthenticatedUser authenticatedUser, String channelId) {
        String normalizedChannelId = normalizeRequired(channelId, "channelId는 필수입니다.");
        favoriteStreamerRepository.findByUserIdAndChannelId(authenticatedUser.id(), normalizedChannelId)
            .ifPresent(favoriteStreamerRepository::delete);
    }

    private FavoriteStreamerResponse toResponse(FavoriteStreamer favoriteStreamer) {
        return new FavoriteStreamerResponse(
            favoriteStreamer.getId(),
            favoriteStreamer.getChannelId(),
            favoriteStreamer.getChannelTitle(),
            favoriteStreamer.getThumbnailUrl(),
            favoriteStreamer.getCreatedAt()
        );
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
