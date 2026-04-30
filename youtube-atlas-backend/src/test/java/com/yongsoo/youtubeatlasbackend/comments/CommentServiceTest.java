package com.yongsoo.youtubeatlasbackend.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AuthException;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.api.ChatMessageResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.CreateCommentRequest;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightState;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightStateRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTier;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTierRepository;
import com.yongsoo.youtubeatlasbackend.game.GameTierService;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

class CommentServiceTest {

    private CommentRepository commentRepository;
    private SimpMessagingTemplate messagingTemplate;
    private CommentPresenceService commentPresenceService;
    private GameSeasonRepository gameSeasonRepository;
    private GameWalletRepository gameWalletRepository;
    private GameHighlightStateRepository gameHighlightStateRepository;
    private GameSeasonTierRepository gameSeasonTierRepository;
    private GameTierService gameTierService;
    private AchievementTitleService achievementTitleService;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentRepository = org.mockito.Mockito.mock(CommentRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        commentPresenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gameHighlightStateRepository = org.mockito.Mockito.mock(GameHighlightStateRepository.class);
        gameSeasonTierRepository = org.mockito.Mockito.mock(GameSeasonTierRepository.class);
        gameTierService = org.mockito.Mockito.mock(GameTierService.class);
        achievementTitleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC);
        commentService = new CommentService(
            commentRepository,
            messagingTemplate,
            commentPresenceService,
            gameSeasonRepository,
            gameWalletRepository,
            gameHighlightStateRepository,
            gameSeasonTierRepository,
            gameTierService,
            achievementTitleService,
            fixedClock
        );
    }

    @Test
    void getCommentsWithoutSinceReturnsLatestTwentyInAscendingOrder() {
        Comment latestComment = comment(22L, "client-22", "최근 메시지", Instant.parse("2026-03-24T10:00:22Z"));
        Comment olderComment = comment(3L, "client-3", "20개 중 가장 오래된 메시지", Instant.parse("2026-03-24T10:00:03Z"));

        when(commentRepository.findTop20ByVideoIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID))
            .thenReturn(List.of(latestComment, olderComment));

        List<ChatMessageResponse> response = commentService.getComments((Instant) null);

        assertThat(response).extracting(ChatMessageResponse::id).containsExactly(3L, 22L);
        verify(commentRepository).findTop20ByVideoIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID);
    }

    @Test
    void getCommentsOnlyReturnsMessagesCreatedAfterSince() {
        Instant since = Instant.parse("2026-03-24T10:00:00Z");
        Comment newerComment = comment(2L, "client-2", "지금 들어온 메시지", Instant.parse("2026-03-24T10:00:01Z"));

        when(commentRepository.findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(CommentService.GLOBAL_ROOM_VIDEO_ID, since))
            .thenReturn(List.of(newerComment));

        List<ChatMessageResponse> response = commentService.getComments(since);

        assertThat(response).singleElement().satisfies(message -> {
            assertThat(message.id()).isEqualTo(2L);
            assertThat(message.content()).isEqualTo("지금 들어온 메시지");
            assertThat(message.systemEventType()).isNull();
        });
        verify(commentRepository).findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(CommentService.GLOBAL_ROOM_VIDEO_ID, since);
    }

    @Test
    void getCommentsIncludesCurrentTierCodeForRequestedRegion() {
        AppUser user = appUser(7L);
        GameSeason season = season(11L, "KR");
        GameWallet wallet = wallet(season, user, 2_000L);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", 0L, 1);
        GameSeasonTier goldTier = tier(season, "GOLD", 15_000L, 2);
        List<GameSeasonTier> tiers = List.of(bronzeTier, goldTier);
        Comment comment = comment(7L, "client-7", "티어 색 메시지", Instant.parse("2026-03-24T10:00:07Z"));
        comment.setUserId(7L);

        when(commentRepository.findTop20ByVideoIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID))
            .thenReturn(List.of(comment));
        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(11L, 7L)).thenReturn(Optional.of(wallet));
        when(gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(11L)).thenReturn(tiers);
        when(gameHighlightStateRepository
            .findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(
                11L,
                7L,
                0L
            ))
            .thenReturn(List.of(highlightState(14_000L)));
        when(gameTierService.resolveTier(tiers, 16_000L)).thenReturn(goldTier);

        List<ChatMessageResponse> response = commentService.getComments(null, "kr");

        assertThat(response).singleElement().satisfies(message -> {
            assertThat(message.userId()).isEqualTo(7L);
            assertThat(message.currentTierCode()).isEqualTo("GOLD");
        });
    }

    @Test
    void getCommentsIncludesSelectedAchievementTitle() {
        Comment comment = comment(7L, "client-7", "칭호 달고 채팅", Instant.parse("2026-03-24T10:00:07Z"));
        comment.setUserId(7L);

        when(commentRepository.findTop20ByVideoIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID))
            .thenReturn(List.of(comment));
        when(achievementTitleService.findSelectedTitlesByUserIds(List.of(7L)))
            .thenReturn(Map.of(7L, selectedTitle()));

        List<ChatMessageResponse> response = commentService.getComments((Instant) null);

        assertThat(response).singleElement().satisfies(message -> {
            assertThat(message.selectedAchievementTitle()).isNotNull();
            assertThat(message.selectedAchievementTitle().code()).isEqualTo("ATLAS_SNIPER");
        });
    }

    @Test
    void createCommentNormalizesContentAndPublishesRealtimeMessage() {
        when(commentRepository.findTopByVideoIdAndUserIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, 7L))
            .thenReturn(Optional.empty());
        when(commentRepository.existsByVideoIdAndUserIdAndContentAndCreatedAtAfter(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq(7L),
            eq("hello world"),
            any(Instant.class)
        )).thenReturn(false);
        when(achievementTitleService.findSelectedTitlesByUserIds(List.of(7L)))
            .thenReturn(Map.of(7L, selectedTitle()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 1L);
            return comment;
        });

        ChatMessageResponse response = commentService.createComment(
            "video-1",
            new CreateCommentRequest("  ", " hello   world ", " client-1 "),
            authenticatedUser()
        );

        assertThat(response.author()).isEqualTo("Atlas User");
        assertThat(response.content()).isEqualTo("hello world");
        assertThat(response.messageType()).isEqualTo(CommentService.USER_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isNull();
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.selectedAchievementTitle()).isNotNull();
        assertThat(response.selectedAchievementTitle().shortName()).isEqualTo("A. Sniper");
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
        verify(commentPresenceService).rememberParticipantName("client-1", "Atlas User");
        verify(messagingTemplate).convertAndSend("/topic/comments", response);
    }

    @Test
    void createCommentRejectsCooldownViolations() {
        Comment latestComment = new Comment();
        latestComment.setVideoId("video-1");
        latestComment.setClientId("client-1");
        latestComment.setUserId(7L);
        latestComment.setCreatedAt(Instant.parse("2026-03-24T09:59:57Z"));

        when(commentRepository.findTopByVideoIdAndUserIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, 7L))
            .thenReturn(Optional.of(latestComment));

        assertThatThrownBy(() -> commentService.createComment(
            "video-1",
            new CreateCommentRequest("익명", "다음 메시지", "client-1"),
            authenticatedUser()
        ))
            .isInstanceOf(CommentPolicyViolationException.class)
            .hasMessageContaining("초 후에 다시");

        verify(commentRepository, never()).save(any(Comment.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void createCommentRejectsUnauthenticatedUser() {
        assertThatThrownBy(() -> commentService.createComment(
            new CreateCommentRequest("익명", "로그인 없이 보내기", "client-1")
        ))
            .isInstanceOf(AuthException.class)
            .hasMessage("로그인이 필요합니다.");

        verify(commentRepository, never()).save(any(Comment.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void createCommentUsesAuthenticatedUserDisplayName() {
        when(commentRepository.findTopByVideoIdAndUserIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, 7L))
            .thenReturn(Optional.empty());
        when(commentRepository.existsByVideoIdAndUserIdAndContentAndCreatedAtAfter(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq(7L),
            eq("로그인한 사용자입니다"),
            any(Instant.class)
        )).thenReturn(false);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 2L);
            return comment;
        });

        ChatMessageResponse response = commentService.createComment(
            "video-1",
            new CreateCommentRequest("익명", "로그인한 사용자입니다", "client-1"),
            authenticatedUser()
        );

        assertThat(response.author()).isEqualTo("Atlas User");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.systemEventType()).isNull();
        verify(commentPresenceService).rememberParticipantName("client-1", "Atlas User");
    }

    @Test
    void createCommentAppliesCooldownByAuthenticatedUserAcrossDevices() {
        Comment latestComment = new Comment();
        latestComment.setVideoId(CommentService.GLOBAL_ROOM_VIDEO_ID);
        latestComment.setClientId("other-device-client");
        latestComment.setUserId(7L);
        latestComment.setCreatedAt(Instant.parse("2026-03-24T09:59:57Z"));

        when(commentRepository.findTopByVideoIdAndUserIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, 7L))
            .thenReturn(Optional.of(latestComment));

        assertThatThrownBy(() -> commentService.createComment(
            "video-1",
            new CreateCommentRequest("익명", "다음 메시지", "client-1"),
            authenticatedUser()
        ))
            .isInstanceOf(CommentPolicyViolationException.class)
            .hasMessageContaining("초 후에 다시");

        verify(commentRepository, never()).save(any(Comment.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void publishTradeSystemMessageStoresAndPublishesSystemMessage() {
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 3L);
            return comment;
        });
        when(commentRepository.findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("system:trade"),
            eq("Atlas User님이 [Title] 1개를 매수했습니다. (7500P)"),
            any(Instant.class)
        )).thenReturn(Optional.empty());

        ChatMessageResponse response = commentService.publishTradeSystemMessage("Atlas User님이 [Title] 1개를 매수했습니다. (7500P)");

        assertThat(response.messageType()).isEqualTo(CommentService.SYSTEM_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isEqualTo(CommentService.TRADE_SYSTEM_EVENT_TYPE);
        assertThat(response.author()).isEqualTo("시스템");
        assertThat(response.clientId()).isEqualTo("system:trade");
        assertThat(response.userId()).isNull();
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
        verify(messagingTemplate).convertAndSend("/topic/comments", response);
    }

    @Test
    void publishLoginSystemMessageStoresAndPublishesSystemMessage() {
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 4L);
            return comment;
        });
        when(commentRepository.findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("system:login"),
            eq("Atlas User님이 로그인했습니다."),
            any(Instant.class)
        )).thenReturn(Optional.empty());

        ChatMessageResponse response = commentService.publishLoginSystemMessage("Atlas User님이 로그인했습니다.");

        assertThat(response.messageType()).isEqualTo(CommentService.SYSTEM_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isEqualTo(CommentService.LOGIN_SYSTEM_EVENT_TYPE);
        assertThat(response.author()).isEqualTo("시스템");
        assertThat(response.clientId()).isEqualTo("system:login");
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
        verify(messagingTemplate).convertAndSend("/topic/comments", response);
    }

    @Test
    void publishTierSystemMessageStoresAndPublishesSystemMessage() {
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 6L);
            return comment;
        });
        when(commentRepository.findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("system:tier"),
            eq("Atlas User님이 실버 티어로 상승했습니다."),
            any(Instant.class)
        )).thenReturn(Optional.empty());

        ChatMessageResponse response = commentService.publishTierSystemMessage("Atlas User님이 실버 티어로 상승했습니다.");

        assertThat(response.messageType()).isEqualTo(CommentService.SYSTEM_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isEqualTo(CommentService.TIER_SYSTEM_EVENT_TYPE);
        assertThat(response.author()).isEqualTo("시스템");
        assertThat(response.clientId()).isEqualTo("system:tier");
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
        verify(messagingTemplate).convertAndSend("/topic/comments", response);
    }

    @Test
    void publishLoginSystemMessageDoesNotRepublishRecentDuplicate() {
        Comment existingComment = new Comment();
        ReflectionTestUtils.setField(existingComment, "id", 5L);
        existingComment.setVideoId(CommentService.GLOBAL_ROOM_VIDEO_ID);
        existingComment.setAuthor("시스템");
        existingComment.setClientId("system:login");
        existingComment.setContent("Atlas User님이 로그인했습니다.");
        existingComment.setCreatedAt(Instant.parse("2026-03-24T09:59:55Z"));

        when(commentRepository.findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("system:login"),
            eq("Atlas User님이 로그인했습니다."),
            any(Instant.class)
        )).thenReturn(Optional.of(existingComment));

        ChatMessageResponse response = commentService.publishLoginSystemMessage("Atlas User님이 로그인했습니다.");

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.messageType()).isEqualTo(CommentService.SYSTEM_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isEqualTo(CommentService.LOGIN_SYSTEM_EVENT_TYPE);
        verify(commentRepository, never()).save(any(Comment.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    private Comment comment(Long id, String clientId, String content, Instant createdAt) {
        Comment comment = new Comment();
        ReflectionTestUtils.setField(comment, "id", id);
        comment.setVideoId(CommentService.GLOBAL_ROOM_VIDEO_ID);
        comment.setAuthor("익명");
        comment.setClientId(clientId);
        comment.setContent(content);
        comment.setCreatedAt(createdAt);
        return comment;
    }

    private AppUser appUser(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setDisplayName("Atlas User");
        return user;
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null);
    }

    private SelectedAchievementTitleResponse selectedTitle() {
        return new SelectedAchievementTitleResponse(
            "ATLAS_SNIPER",
            "Atlas Sniper",
            "A. Sniper",
            AchievementTitleGrade.ULTIMATE,
            "모든 전략 칭호를 획득한 유저"
        );
    }

    private GameSeason season(Long id, String regionCode) {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", id);
        season.setRegionCode(regionCode);
        season.setStatus(SeasonStatus.ACTIVE);
        return season;
    }

    private GameWallet wallet(GameSeason season, AppUser user, long manualTierScoreAdjustment) {
        GameWallet wallet = new GameWallet();
        wallet.setSeason(season);
        wallet.setUser(user);
        wallet.setManualTierScoreAdjustment(manualTierScoreAdjustment);
        return wallet;
    }

    private GameSeasonTier tier(GameSeason season, String tierCode, long minScore, int sortOrder) {
        GameSeasonTier tier = new GameSeasonTier();
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(tierCode);
        tier.setMinScore(minScore);
        tier.setSortOrder(sortOrder);
        return tier;
    }

    private GameHighlightState highlightState(long score) {
        GameHighlightState state = new GameHighlightState();
        state.setBestSettledHighlightScore(score);
        return state;
    }
}
