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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.api.ChatMessageResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.CreateCommentRequest;

class CommentServiceTest {

    private CommentRepository commentRepository;
    private SimpMessagingTemplate messagingTemplate;
    private CommentPresenceService commentPresenceService;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentRepository = org.mockito.Mockito.mock(CommentRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        commentPresenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC);
        commentService = new CommentService(commentRepository, messagingTemplate, commentPresenceService, fixedClock);
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
    void createCommentNormalizesContentAndPublishesRealtimeMessage() {
        when(commentRepository.findTopByVideoIdAndClientIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, "client-1"))
            .thenReturn(Optional.empty());
        when(commentRepository.existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("client-1"),
            eq("hello world"),
            any(Instant.class)
        )).thenReturn(false);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0, Comment.class);
            ReflectionTestUtils.setField(comment, "id", 1L);
            return comment;
        });

        ChatMessageResponse response = commentService.createComment(
            "video-1",
            new CreateCommentRequest("  ", " hello   world ", " client-1 ")
        );

        assertThat(response.author()).isEqualTo("익명");
        assertThat(response.content()).isEqualTo("hello world");
        assertThat(response.messageType()).isEqualTo(CommentService.USER_MESSAGE_TYPE);
        assertThat(response.systemEventType()).isNull();
        assertThat(response.userId()).isNull();
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
        verify(commentPresenceService).rememberParticipantName("client-1", "익명");
        verify(messagingTemplate).convertAndSend("/topic/comments", response);
    }

    @Test
    void createCommentRejectsCooldownViolations() {
        Comment latestComment = new Comment();
        latestComment.setVideoId("video-1");
        latestComment.setClientId("client-1");
        latestComment.setCreatedAt(Instant.parse("2026-03-24T09:59:57Z"));

        when(commentRepository.findTopByVideoIdAndClientIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, "client-1"))
            .thenReturn(Optional.of(latestComment));

        assertThatThrownBy(() -> commentService.createComment(
            "video-1",
            new CreateCommentRequest("익명", "다음 메시지", "client-1")
        ))
            .isInstanceOf(CommentPolicyViolationException.class)
            .hasMessageContaining("초 후에 다시");

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
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null)
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
            new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null)
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
}
