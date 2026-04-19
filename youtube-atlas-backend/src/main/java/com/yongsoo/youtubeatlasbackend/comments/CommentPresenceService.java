package com.yongsoo.youtubeatlasbackend.comments;

import java.util.Set;
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

    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    private final SimpMessagingTemplate messagingTemplate;

    public CommentPresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ChatPresenceResponse getPresence() {
        return new ChatPresenceResponse(activeSessionIds.size());
    }

    @EventListener
    public void onSessionConnected(SessionConnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        handleSessionConnected(sessionId);
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        handleSessionDisconnected(event.getSessionId());
    }

    void handleSessionConnected(String sessionId) {
        if (sessionId == null || !activeSessionIds.add(sessionId)) {
            return;
        }

        publishPresence();
    }

    void handleSessionDisconnected(String sessionId) {
        if (sessionId == null || !activeSessionIds.remove(sessionId)) {
            return;
        }

        publishPresence();
    }

    private void publishPresence() {
        messagingTemplate.convertAndSend(COMMENTS_PRESENCE_TOPIC, getPresence());
    }
}
