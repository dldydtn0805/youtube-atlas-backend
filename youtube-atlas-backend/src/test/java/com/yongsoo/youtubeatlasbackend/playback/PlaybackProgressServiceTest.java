package com.yongsoo.youtubeatlasbackend.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.playback.api.UpsertPlaybackProgressRequest;

class PlaybackProgressServiceTest {

    private PlaybackProgressRepository playbackProgressRepository;
    private AppUserRepository appUserRepository;
    private PlaybackProgressService playbackProgressService;

    @BeforeEach
    void setUp() {
        playbackProgressRepository = org.mockito.Mockito.mock(PlaybackProgressRepository.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);
        playbackProgressService = new PlaybackProgressService(
            playbackProgressRepository,
            appUserRepository,
            fixedClock
        );
    }

    @Test
    void upsertProgressCreatesPlaybackStateForUser() {
        AppUser appUser = new AppUser();
        ReflectionTestUtils.setField(appUser, "id", 7L);

        when(playbackProgressRepository.findByUserIdAndVideoId(7L, "abc123")).thenReturn(Optional.empty());
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(playbackProgressRepository.save(any(PlaybackProgress.class))).thenAnswer(invocation -> {
            PlaybackProgress playbackProgress = invocation.getArgument(0, PlaybackProgress.class);
            ReflectionTestUtils.setField(playbackProgress, "id", 100L);
            return playbackProgress;
        });

        var response = playbackProgressService.upsertProgress(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            new UpsertPlaybackProgressRequest(
                " abc123 ",
                " Sample title ",
                " Sample channel ",
                " https://example.com/thumb.jpg ",
                184L
            )
        );

        assertThat(response.videoId()).isEqualTo("abc123");
        assertThat(response.videoTitle()).isEqualTo("Sample title");
        assertThat(response.channelTitle()).isEqualTo("Sample channel");
        assertThat(response.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(response.positionSeconds()).isEqualTo(184L);
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
    }

    @Test
    void upsertProgressReusesExistingPlaybackStateForSameVideo() {
        AppUser appUser = new AppUser();
        ReflectionTestUtils.setField(appUser, "id", 7L);

        PlaybackProgress playbackProgress = new PlaybackProgress();
        ReflectionTestUtils.setField(playbackProgress, "id", 100L);
        playbackProgress.setUser(appUser);
        playbackProgress.setUpdatedAt(Instant.parse("2026-03-31T06:00:00Z"));

        when(playbackProgressRepository.findByUserIdAndVideoId(7L, "video-2")).thenReturn(Optional.of(playbackProgress));
        when(playbackProgressRepository.save(playbackProgress)).thenReturn(playbackProgress);

        var response = playbackProgressService.upsertProgress(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            new UpsertPlaybackProgressRequest("video-2", null, null, null, 42L)
        );

        verify(appUserRepository, never()).findById(any());
        assertThat(response.videoId()).isEqualTo("video-2");
        assertThat(response.positionSeconds()).isEqualTo(42L);
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
    }

    @Test
    void getCurrentProgressReturnsStoredPlaybackState() {
        PlaybackProgress playbackProgress = new PlaybackProgress();
        playbackProgress.setVideoId("abc123");
        playbackProgress.setVideoTitle("Sample title");
        playbackProgress.setChannelTitle("Sample channel");
        playbackProgress.setThumbnailUrl("https://example.com/thumb.jpg");
        playbackProgress.setPositionSeconds(184L);
        playbackProgress.setUpdatedAt(Instant.parse("2026-04-01T05:50:00Z"));

        when(playbackProgressRepository.findTopByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(Optional.of(playbackProgress));

        var response = playbackProgressService.getCurrentProgress(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null)
        );

        assertThat(response).isPresent();
        assertThat(response.get().videoId()).isEqualTo("abc123");
        assertThat(response.get().positionSeconds()).isEqualTo(184L);
    }

    @Test
    void getRecentProgressesReturnsStoredPlaybackStatesInUpdatedOrder() {
        PlaybackProgress latest = new PlaybackProgress();
        latest.setVideoId("latest");
        latest.setPositionSeconds(42L);
        latest.setUpdatedAt(Instant.parse("2026-04-01T06:00:00Z"));

        PlaybackProgress previous = new PlaybackProgress();
        previous.setVideoId("previous");
        previous.setPositionSeconds(84L);
        previous.setUpdatedAt(Instant.parse("2026-04-01T05:00:00Z"));

        when(playbackProgressRepository.findByUserIdOrderByUpdatedAtDesc(7L, PageRequest.of(0, 5)))
            .thenReturn(List.of(latest, previous));

        var response = playbackProgressService.getRecentProgressesForUserId(7L, 5);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).videoId()).isEqualTo("latest");
        assertThat(response.get(1).videoId()).isEqualTo("previous");
    }
}
