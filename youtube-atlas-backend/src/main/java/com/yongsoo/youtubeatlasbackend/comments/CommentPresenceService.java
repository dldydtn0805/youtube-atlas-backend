package com.yongsoo.youtubeatlasbackend.comments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.yongsoo.youtubeatlasbackend.comments.api.ChatPresenceResponse;

@Service
public class CommentPresenceService {

    public static final String COMMENTS_PRESENCE_TOPIC = "/topic/comments/presence";
    public static final String PARTICIPANT_HEADER = "x-participant-id";

    private final Map<String, String> participantIdBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeParticipantCounts = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public CommentPresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ChatPresenceResponse getPresence() {
        return new ChatPresenceResponse(activeParticipantCounts.size());
    }

    @EventListener
    public void onSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        handleSessionConnected(accessor.getSessionId(), accessor.getFirstNativeHeader(PARTICIPANT_HEADER));
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        handleSessionDisconnected(event.getSessionId());
    }

    void handleSessionConnected(String sessionId, String participantId) {
        if (sessionId == null || participantIdBySessionId.containsKey(sessionId)) {
            return;
        }

        String nextParticipantId = normalizeParticipantId(sessionId, participantId);
        if (nextParticipantId == null) {
            return;
        }
        participantIdBySessionId.put(sessionId, nextParticipantId);
        activeParticipantCounts.merge(nextParticipantId, 1, Integer::sum);
        publishPresence();
    }

    void handleSessionDisconnected(String sessionId) {
        if (sessionId == null) {
            return;
        }

        String participantId = participantIdBySessionId.remove(sessionId);

        if (participantId == null) {
            return;
        }

        activeParticipantCounts.computeIfPresent(participantId, (_key, count) -> count > 1 ? count - 1 : null);
        publishPresence();
    }

    private String normalizeParticipantId(String sessionId, String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return null;
        }

        return participantId.trim();
    }

    private void publishPresence() {
        messagingTemplate.convertAndSend(COMMENTS_PRESENCE_TOPIC, getPresence());
    }
}
