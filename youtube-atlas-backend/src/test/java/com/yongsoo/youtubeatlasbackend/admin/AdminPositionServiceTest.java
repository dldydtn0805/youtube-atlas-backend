package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.yongsoo.youtubeatlasbackend.admin.api.AdminPositionUpdateRequest;
import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.game.GamePosition;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class AdminPositionServiceTest {

    private AppUserRepository appUserRepository;
    private GamePositionRepository gamePositionRepository;
    private GameWalletRepository gameWalletRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private AdminPositionService adminPositionService;

    @BeforeEach
    void setUp() {
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-15T03:00:00Z"), ZoneOffset.UTC);
        adminPositionService = new AdminPositionService(
            appUserRepository,
            gamePositionRepository,
            gameWalletRepository,
            trendSnapshotRepository,
            clock
        );
    }

    @Test
    void getOpenPositionsReturnsSeasonScopedOpenPositions() {
        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(3L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of(openPosition(11L, 3L, "Season 3", 1200L, 200L)));

        var response = adminPositionService.getOpenPositions(7L, 3L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(11L);
        assertThat(response.get(0).seasonId()).isEqualTo(3L);
        assertThat(response.get(0).quantity()).isEqualTo(200L);
        assertThat(response.get(0).stakePoints()).isEqualTo(1200L);
    }

    @Test
    void updateOpenPositionAdjustsWalletBalanceAndReservedPoints() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);
        wallet.setRealizedPnlPoints(0L);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = adminPositionService.updateOpenPosition(7L, 11L, new AdminPositionUpdateRequest(300L, 1500L, null));

        assertThat(response.quantity()).isEqualTo(300L);
        assertThat(response.stakePoints()).isEqualTo(1500L);
        assertThat(position.getQuantity()).isEqualTo(300L);
        assertThat(position.getStakePoints()).isEqualTo(1500L);
        assertThat(wallet.getBalancePoints()).isEqualTo(8500L);
        assertThat(wallet.getReservedPoints()).isEqualTo(1500L);
        assertThat(wallet.getUpdatedAt()).isEqualTo(Instant.parse("2026-04-15T03:00:00Z"));
        verify(gameWalletRepository).save(wallet);
    }

    @Test
    void updateOpenPositionRecalculatesBuySnapshotFromCreatedAt() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);
        wallet.setRealizedPnlPoints(0L);
        Instant requestedCreatedAt = Instant.parse("2026-04-01T01:30:00Z");
        TrendSnapshot snapshot = snapshot(55L, requestedCreatedAt.minusSeconds(300), 42);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));
        when(trendSnapshotRepository.findSnapshotsByRegionCodeAndCategoryIdAndVideoIdCapturedBeforeOrderByCapturedAtDesc(
            "KR",
            "music",
            "video-1",
            requestedCreatedAt
        )).thenReturn(List.of(snapshot));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = adminPositionService.updateOpenPosition(
            7L,
            11L,
            new AdminPositionUpdateRequest(300L, 1500L, requestedCreatedAt)
        );

        assertThat(response.buyRank()).isEqualTo(42);
        assertThat(response.buyCapturedAt()).isEqualTo(Instant.parse("2026-04-01T01:25:00Z"));
        assertThat(response.createdAt()).isEqualTo(requestedCreatedAt);
        assertThat(position.getBuyRunId()).isEqualTo(55L);
        assertThat(position.getBuyRank()).isEqualTo(42);
        assertThat(position.getBuyCapturedAt()).isEqualTo(Instant.parse("2026-04-01T01:25:00Z"));
        assertThat(position.getCreatedAt()).isEqualTo(requestedCreatedAt);
        assertThat(position.getTitle()).isEqualTo("Snapshot title");
        assertThat(position.getChannelTitle()).isEqualTo("Snapshot channel");
        assertThat(position.getThumbnailUrl()).isEqualTo("https://example.com/snapshot.jpg");
    }

    @Test
    void updateOpenPositionRejectsNonOpenPosition() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        position.setStatus(PositionStatus.CLOSED);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(7L, 11L, new AdminPositionUpdateRequest(300L, 1500L, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OPEN");
    }

    @Test
    void updateOpenPositionRejectsNonStepQuantity() {
        when(appUserRepository.existsById(7L)).thenReturn(true);

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(7L, 11L, new AdminPositionUpdateRequest(250L, 1500L, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100");
    }

    @Test
    void updateOpenPositionRejectsCreatedAtWithoutEarlierSnapshot() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));
        when(trendSnapshotRepository.findSnapshotsByRegionCodeAndCategoryIdAndVideoIdCapturedBeforeOrderByCapturedAtDesc(
            "KR",
            "music",
            "video-1",
            Instant.parse("2026-03-01T00:00:00Z")
        )).thenReturn(List.of());

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(
            7L,
            11L,
            new AdminPositionUpdateRequest(300L, 1500L, Instant.parse("2026-04-01T00:00:01Z"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("스냅샷");
    }

    @Test
    void updateOpenPositionRejectsFutureCreatedAt() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(
            7L,
            11L,
            new AdminPositionUpdateRequest(300L, 1500L, Instant.parse("2026-04-16T00:00:00Z"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("현재 시각 이후");
    }

    @Test
    void updateOpenPositionRejectsCreatedAtBeforeSeasonStart() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(
            7L,
            11L,
            new AdminPositionUpdateRequest(300L, 1500L, Instant.parse("2026-03-31T23:59:59Z"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("시즌 시작");
    }

    @Test
    void updateOpenPositionRejectsCreatedAtAfterSeasonEnd() {
        GamePosition position = openPosition(11L, 3L, "Season 3", 1200L, 200L);
        position.getSeason().setEndAt(Instant.parse("2026-04-10T00:00:00Z"));
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(8800L);
        wallet.setReservedPoints(1200L);

        when(appUserRepository.existsById(7L)).thenReturn(true);
        when(gamePositionRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(3L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> adminPositionService.updateOpenPosition(
            7L,
            11L,
            new AdminPositionUpdateRequest(300L, 1500L, Instant.parse("2026-04-10T00:00:01Z"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("시즌 종료");
    }

    private GamePosition openPosition(Long id, Long seasonId, String seasonName, Long stakePoints, Long quantity) {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", seasonId);
        season.setName(seasonName);
        season.setStartAt(Instant.parse("2026-04-01T00:00:00Z"));
        season.setEndAt(Instant.parse("2026-04-20T00:00:00Z"));

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", id);
        position.setSeason(season);
        position.setUser(user);
        position.setRegionCode("KR");
        position.setCategoryId("music");
        position.setVideoId("video-1");
        position.setTitle("Sample");
        position.setChannelTitle("Atlas TV");
        position.setThumbnailUrl("https://example.com/thumb.jpg");
        position.setBuyRank(12);
        position.setQuantity(quantity);
        position.setStakePoints(stakePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setBuyCapturedAt(Instant.parse("2026-04-01T00:00:00Z"));
        position.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return position;
    }

    private TrendSnapshot snapshot(Long runId, Instant capturedAt, Integer rank) {
        TrendRun run = new TrendRun();
        ReflectionTestUtils.setField(run, "id", runId);
        run.setRegionCode("KR");
        run.setCategoryId("music");
        run.setCategoryLabel("Music");
        run.setSourceCategoryIds(List.of());
        run.setSource("youtube-mostPopular");
        run.setCapturedAt(capturedAt);

        TrendSnapshot snapshot = new TrendSnapshot();
        ReflectionTestUtils.setField(snapshot, "id", 99L);
        snapshot.setRun(run);
        snapshot.setRegionCode("KR");
        snapshot.setCategoryId("music");
        snapshot.setVideoId("video-1");
        snapshot.setRank(rank);
        snapshot.setTitle("Snapshot title");
        snapshot.setChannelTitle("Snapshot channel");
        snapshot.setChannelId("channel-1");
        snapshot.setThumbnailUrl("https://example.com/snapshot.jpg");
        snapshot.setCreatedAt(capturedAt);
        return snapshot;
    }
}
