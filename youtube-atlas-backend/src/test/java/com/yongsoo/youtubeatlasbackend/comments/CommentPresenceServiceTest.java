package com.yongsoo.youtubeatlasbackend.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class CommentPresenceServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private CommentPresenceService commentPresenceService;

    @BeforeEach
    void setUp() {
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        commentPresenceService = new CommentPresenceService(messagingTemplate);
    }

    @Test
    void tracksUniqueParticipantsAndPublishesPresence() {
        commentPresenceService.handleSessionConnected("session-1", "participant-1");
        commentPresenceService.handleSessionConnected("session-2", "participant-2");

        assertThat(commentPresenceService.getPresence().activeCount()).isEqualTo(2);
        verify(messagingTemplate).convertAndSend(
            CommentPresenceService.COMMENTS_PRESENCE_TOPIC,
            commentPresenceService.getPresence()
        );
    }

    @Test
    void ignoresDuplicateSessionConnectsAndUnknownDisconnects() {
        commentPresenceService.handleSessionConnected("session-1", "participant-1");
        commentPresenceService.handleSessionConnected("session-1", "participant-1");
        commentPresenceService.handleSessionDisconnected("missing-session");

        assertThat(commentPresenceService.getPresence().activeCount()).isEqualTo(1);
        verify(messagingTemplate, never()).convertAndSend(
            CommentPresenceService.COMMENTS_PRESENCE_TOPIC,
            new com.yongsoo.youtubeatlasbackend.comments.api.ChatPresenceResponse(0)
        );
    }

    @Test
    void countsMultipleSessionsFromTheSameParticipantOnce() {
        commentPresenceService.handleSessionConnected("session-1", "participant-1");
        commentPresenceService.handleSessionConnected("session-2", "participant-1");

        assertThat(commentPresenceService.getPresence().activeCount()).isEqualTo(1);
    }

    @Test
    void removesDisconnectedSessionsAndPublishesPresence() {
        commentPresenceService.handleSessionConnected("session-1", "participant-1");
        commentPresenceService.handleSessionConnected("session-2", "participant-2");

        commentPresenceService.handleSessionDisconnected("session-1");

        assertThat(commentPresenceService.getPresence().activeCount()).isEqualTo(1);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            CommentPresenceService.COMMENTS_PRESENCE_TOPIC,
            commentPresenceService.getPresence()
        );
    }

    @Test
    void ignoresSessionsWithoutParticipantHeader() {
        commentPresenceService.handleSessionConnected("session-1", null);

        assertThat(commentPresenceService.getPresence().activeCount()).isEqualTo(0);
    }
}
