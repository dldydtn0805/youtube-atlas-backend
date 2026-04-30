package com.yongsoo.youtubeatlasbackend.comments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.CommentPresenceService;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;

class CommentControllerTest {

    @Test
    void updatesPresenceDisplayNameForTheAuthenticatedParticipant() {
        CommentService commentService = org.mockito.Mockito.mock(CommentService.class);
        CommentPresenceService presenceService = org.mockito.Mockito.mock(CommentPresenceService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        ChatPresenceResponse presence = new ChatPresenceResponse(
            1,
            List.of(new ChatPresenceParticipantResponse("client-1", "Atlas User"))
        );
        CommentController controller = new CommentController(commentService, presenceService, authService);

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
}
