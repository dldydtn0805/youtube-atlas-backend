package com.yongsoo.youtubeatlasbackend.comments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.auth.AuthException;
import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.CommentHighlight;
import com.yongsoo.youtubeatlasbackend.comments.CommentHighlightService;
import com.yongsoo.youtubeatlasbackend.comments.CommentPresenceService;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

class CommentControllerTest {

    @Test
    void updatesPresenceDisplayNameForTheAuthenticatedParticipant() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentHighlightService highlightService = org.mockito.Mockito.mock(CommentHighlightService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AchievementTitleService titleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        ChatPresenceResponse presence = new ChatPresenceResponse(
            1,
            List.of(new ChatPresenceParticipantResponse("client-1", "Atlas User"))
        );
        CommentController controller = new CommentController(commentService, highlightService, presenceService, titleService, authService);

        when(authService.requireCurrentUser("Bearer token"))
            .thenReturn(new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null));
        when(presenceService.rememberParticipantName("client-1", "Atlas User")).thenReturn(presence);

        ChatPresenceResponse response = controller.updateCommentPresence(
            "Bearer token",
            new UpdateCommentPresenceRequest("client-1")
        );

        assertThat(response).isSameAs(presence);
        verify(presenceService).rememberParticipantName("client-1", "Atlas User");
    }

    @Test
    void createGlobalCommentRequiresAuthenticatedUser() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentHighlightService highlightService = org.mockito.Mockito.mock(CommentHighlightService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AchievementTitleService titleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        CommentController controller = new CommentController(commentService, highlightService, presenceService, titleService, authService);
        AuthenticatedUser user = authenticatedUser();
        CreateCommentRequest request = new CreateCommentRequest("익명", "로그인 채팅", "client-1");
        ChatMessageResponse message = message(1L);

        when(authService.requireCurrentUser("Bearer token")).thenReturn(user);
        when(commentService.createComment(request, user)).thenReturn(message);

        ChatMessageResponse response = controller.createGlobalComment("Bearer token", request);

        assertThat(response).isSameAs(message);
        verify(authService).requireCurrentUser("Bearer token");
        verify(commentService).createComment(request, user);
    }

    @Test
    void createVideoCommentRequiresAuthenticatedUser() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentHighlightService highlightService = org.mockito.Mockito.mock(CommentHighlightService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AchievementTitleService titleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        CommentController controller = new CommentController(commentService, highlightService, presenceService, titleService, authService);
        AuthenticatedUser user = authenticatedUser();
        CreateCommentRequest request = new CreateCommentRequest("익명", "영상 채팅", "client-1");
        ChatMessageResponse message = message(2L);

        when(authService.requireCurrentUser("Bearer token")).thenReturn(user);
        when(commentService.createComment("video-1", request, user)).thenReturn(message);

        ChatMessageResponse response = controller.createComment("video-1", "Bearer token", request);

        assertThat(response).isSameAs(message);
        verify(authService).requireCurrentUser("Bearer token");
        verify(commentService).createComment("video-1", request, user);
    }

    @Test
    void createGlobalCommentDoesNotStoreWhenUnauthenticated() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentHighlightService highlightService = org.mockito.Mockito.mock(CommentHighlightService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AchievementTitleService titleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        CommentController controller = new CommentController(commentService, highlightService, presenceService, titleService, authService);
        CreateCommentRequest request = new CreateCommentRequest("익명", "익명 채팅", "client-1");

        when(authService.requireCurrentUser(null))
            .thenThrow(new AuthException("unauthorized", "로그인이 필요합니다."));

        assertThatThrownBy(() -> controller.createGlobalComment(null, request))
            .isInstanceOf(AuthException.class)
            .hasMessage("로그인이 필요합니다.");

        verify(commentService, never()).createComment(request, null);
    }

    @Test
    void getsPublicCommentHighlightsWithoutAuthentication() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentHighlightService highlightService = org.mockito.Mockito.mock(CommentHighlightService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AchievementTitleService titleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        CommentController controller = new CommentController(commentService, highlightService, presenceService, titleService, authService);

        when(highlightService.getHighlights("video-1")).thenReturn(List.of(
            new CommentHighlight("comment-1", "@Viewer", "좋아요 많은 댓글", 42L)
        ));
        when(titleService.getPublicTitles()).thenReturn(List.of(
            new SelectedAchievementTitleResponse(
                "ATLAS_SNIPER",
                "Atlas Sniper",
                "A. Sniper",
                AchievementTitleGrade.ULTIMATE,
                "전 구간 복합 하이라이트 달성자입니다."
            )
        ));

        List<CommentHighlightResponse> response = controller.getCommentHighlights("video-1");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().videoId()).isEqualTo("video-1");
        assertThat(response.getFirst().author()).isEqualTo("@Viewer");
        assertThat(response.getFirst().content()).isEqualTo("좋아요 많은 댓글");
        assertThat(response.getFirst().likeCount()).isEqualTo(42L);
        assertThat(response.getFirst().selectedAchievementTitle().displayName()).isEqualTo("Atlas Sniper");
        assertThat(response.getFirst().selectedAchievementTitle().grade()).isEqualTo(AchievementTitleGrade.ULTIMATE);
        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null);
    }

    private ChatMessageResponse message(Long id) {
        return new ChatMessageResponse(
            id,
            "global",
            "USER",
            null,
            "Atlas User",
            "로그인 채팅",
            "client-1",
            7L,
            null,
            null,
            Instant.parse("2026-03-24T10:00:00Z")
        );
    }
}
