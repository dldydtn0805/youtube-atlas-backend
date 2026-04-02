package com.yongsoo.youtubeatlasbackend.playback;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;
import com.yongsoo.youtubeatlasbackend.playback.api.UpsertPlaybackProgressRequest;

@Service
public class PlaybackProgressService {

    private final PlaybackProgressRepository playbackProgressRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public PlaybackProgressService(
        PlaybackProgressRepository playbackProgressRepository,
        AppUserRepository appUserRepository,
        Clock clock
    ) {
        this.playbackProgressRepository = playbackProgressRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PlaybackProgressResponse> getCurrentProgress(AuthenticatedUser authenticatedUser) {
        return getCurrentProgressForUserId(authenticatedUser.id());
    }

    @Transactional(readOnly = true)
    public Optional<PlaybackProgressResponse> getCurrentProgressForUserId(Long userId) {
        return playbackProgressRepository.findByUserId(userId)
            .map(this::toResponse);
    }

    @Transactional
    public PlaybackProgressResponse upsertProgress(
        AuthenticatedUser authenticatedUser,
        UpsertPlaybackProgressRequest request
    ) {
        String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");

        PlaybackProgress playbackProgress = playbackProgressRepository.findByUserId(authenticatedUser.id())
            .orElseGet(PlaybackProgress::new);

        if (playbackProgress.getUser() == null) {
            AppUser user = appUserRepository.findById(authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
            playbackProgress.setUser(user);
        }

        playbackProgress.setVideoId(videoId);
        playbackProgress.setVideoTitle(normalizeOptional(request.videoTitle()));
        playbackProgress.setChannelTitle(normalizeOptional(request.channelTitle()));
        playbackProgress.setThumbnailUrl(normalizeOptional(request.thumbnailUrl()));
        playbackProgress.setPositionSeconds(request.positionSeconds());
        playbackProgress.setUpdatedAt(Instant.now(clock));

        return toResponse(playbackProgressRepository.save(playbackProgress));
    }

    private PlaybackProgressResponse toResponse(PlaybackProgress playbackProgress) {
        return new PlaybackProgressResponse(
            playbackProgress.getVideoId(),
            playbackProgress.getVideoTitle(),
            playbackProgress.getChannelTitle(),
            playbackProgress.getThumbnailUrl(),
            playbackProgress.getPositionSeconds(),
            playbackProgress.getUpdatedAt()
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
