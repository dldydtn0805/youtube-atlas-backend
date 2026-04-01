package com.yongsoo.youtubeatlasbackend.favorites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.favorites.api.CreateFavoriteStreamerRequest;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;

class FavoriteStreamerServiceTest {

    private FavoriteStreamerRepository favoriteStreamerRepository;
    private AppUserRepository appUserRepository;
    private YouTubeCatalogService youTubeCatalogService;
    private FavoriteStreamerService favoriteStreamerService;

    @BeforeEach
    void setUp() {
        favoriteStreamerRepository = org.mockito.Mockito.mock(FavoriteStreamerRepository.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        youTubeCatalogService = org.mockito.Mockito.mock(YouTubeCatalogService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);
        favoriteStreamerService = new FavoriteStreamerService(
            favoriteStreamerRepository,
            appUserRepository,
            youTubeCatalogService,
            fixedClock
        );
    }

    @Test
    void addFavoriteStreamerCreatesNewFavorite() {
        AppUser appUser = new AppUser();
        ReflectionTestUtils.setField(appUser, "id", 7L);

        when(favoriteStreamerRepository.findByUserIdAndChannelId(7L, "channel-1")).thenReturn(Optional.empty());
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(favoriteStreamerRepository.save(any(FavoriteStreamer.class))).thenAnswer(invocation -> {
            FavoriteStreamer favoriteStreamer = invocation.getArgument(0, FavoriteStreamer.class);
            ReflectionTestUtils.setField(favoriteStreamer, "id", 100L);
            return favoriteStreamer;
        });

        var response = favoriteStreamerService.addFavoriteStreamer(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            new CreateFavoriteStreamerRequest(" channel-1 ", " Streamer One ", " https://example.com/thumb.jpg ")
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.channelId()).isEqualTo("channel-1");
        assertThat(response.channelTitle()).isEqualTo("Streamer One");
        assertThat(response.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
    }

    @Test
    void addFavoriteStreamerReusesExistingFavoriteForSameChannel() {
        FavoriteStreamer favoriteStreamer = new FavoriteStreamer();
        ReflectionTestUtils.setField(favoriteStreamer, "id", 100L);
        favoriteStreamer.setCreatedAt(Instant.parse("2026-03-30T06:00:00Z"));

        when(favoriteStreamerRepository.findByUserIdAndChannelId(7L, "channel-1"))
            .thenReturn(Optional.of(favoriteStreamer));
        when(favoriteStreamerRepository.save(favoriteStreamer)).thenReturn(favoriteStreamer);

        var response = favoriteStreamerService.addFavoriteStreamer(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            new CreateFavoriteStreamerRequest("channel-1", "Updated Streamer", null)
        );

        verify(appUserRepository, never()).findById(any());
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.channelTitle()).isEqualTo("Updated Streamer");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-03-30T06:00:00Z"));
    }

    @Test
    void getFavoriteStreamersReturnsNewestFirst() {
        FavoriteStreamer newer = favoriteStreamer(2L, "channel-2", "Streamer Two", Instant.parse("2026-04-01T06:00:00Z"));
        FavoriteStreamer older = favoriteStreamer(1L, "channel-1", "Streamer One", Instant.parse("2026-03-31T06:00:00Z"));
        when(favoriteStreamerRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(newer, older));

        var responses = favoriteStreamerService.getFavoriteStreamers(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null)
        );

        assertThat(responses).extracting("channelId").containsExactly("channel-2", "channel-1");
    }

    @Test
    void deleteFavoriteStreamerDeletesWhenFound() {
        FavoriteStreamer favoriteStreamer = favoriteStreamer(1L, "channel-1", "Streamer One", Instant.parse("2026-03-31T06:00:00Z"));
        when(favoriteStreamerRepository.findByUserIdAndChannelId(7L, "channel-1"))
            .thenReturn(Optional.of(favoriteStreamer));

        favoriteStreamerService.deleteFavoriteStreamer(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            " channel-1 "
        );

        verify(favoriteStreamerRepository).delete(eq(favoriteStreamer));
    }

    @Test
    void getFavoriteStreamerVideosUsesFavoriteChannelIdsToFilterPopularFeed() {
        FavoriteStreamer newer = favoriteStreamer(2L, "channel-2", "Streamer Two", Instant.parse("2026-04-01T06:00:00Z"));
        FavoriteStreamer older = favoriteStreamer(1L, "channel-1", "Streamer One", Instant.parse("2026-03-31T06:00:00Z"));
        VideoCategorySectionResponse response = new VideoCategorySectionResponse(
            "favorite-streamers",
            "즐겨찾기 채널",
            "전체 인기 영상 중 즐겨찾기한 채널의 영상만 모았습니다.",
            List.of(),
            "NEXT"
        );
        when(favoriteStreamerRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(newer, older));
        when(youTubeCatalogService.getPopularVideosForChannels("KR", List.of("channel-2", "channel-1"), "PAGE-1"))
            .thenReturn(response);

        var result = favoriteStreamerService.getFavoriteStreamerVideos(
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null),
            "KR",
            "PAGE-1"
        );

        assertThat(result).isSameAs(response);
    }

    private FavoriteStreamer favoriteStreamer(Long id, String channelId, String channelTitle, Instant createdAt) {
        FavoriteStreamer favoriteStreamer = new FavoriteStreamer();
        ReflectionTestUtils.setField(favoriteStreamer, "id", id);
        favoriteStreamer.setChannelId(channelId);
        favoriteStreamer.setChannelTitle(channelTitle);
        favoriteStreamer.setCreatedAt(createdAt);
        return favoriteStreamer;
    }
}
