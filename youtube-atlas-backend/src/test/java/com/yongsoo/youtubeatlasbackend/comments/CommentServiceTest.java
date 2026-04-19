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
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentRepository = org.mockito.Mockito.mock(CommentRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC);
        commentService = new CommentService(commentRepository, messagingTemplate, fixedClock);
    }

    @Test
    void getCommentsOnlyReturnsMessagesCreatedAfterSince() {
        Instant since = Instant.parse("2026-03-24T10:00:00Z");
        Comment newerComment = new Comment();
        ReflectionTestUtils.setField(newerComment, "id", 2L);
        newerComment.setVideoId("video-1");
        newerComment.setAuthor("익명");
        newerComment.setClientId("client-2");
        newerComment.setContent("지금 들어온 메시지");
        newerComment.setCreatedAt(Instant.parse("2026-03-24T10:00:01Z"));

        when(commentRepository.findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(CommentService.GLOBAL_ROOM_VIDEO_ID, since))
            .thenReturn(List.of(newerComment));

        List<ChatMessageResponse> response = commentService.getComments(since);

        assertThat(response).singleElement().satisfies(message -> {
            assertThat(message.id()).isEqualTo(2L);
            assertThat(message.content()).isEqualTo("지금 들어온 메시지");
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
        assertThat(response.videoId()).isEqualTo(CommentService.GLOBAL_ROOM_VIDEO_ID);
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
        when(commentRepository.findTopByVideoIdAndClientIdOrderByCreatedAtDesc(CommentService.GLOBAL_ROOM_VIDEO_ID, "client-1"))
            .thenReturn(Optional.empty());
        when(commentRepository.existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
            eq(CommentService.GLOBAL_ROOM_VIDEO_ID),
            eq("client-1"),
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
    }
}
