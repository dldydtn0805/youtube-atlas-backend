package com.yongsoo.youtubeatlasbackend.comments;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.yongsoo.youtubeatlasbackend.comments.api.ChatPresenceParticipantResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.ChatPresenceResponse;

@Service
public class CommentPresenceService {

    public static final String COMMENTS_PRESENCE_TOPIC = "/topic/comments/presence";
    public static final String PARTICIPANT_HEADER = "x-participant-id";

    private final Map<String, String> participantIdBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeParticipantCounts = new ConcurrentHashMap<>();
    private final Map<String, String> participantNameById = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public CommentPresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ChatPresenceResponse getPresence() {
        List<ChatPresenceParticipantResponse> participants = activeParticipantCounts.keySet().stream()
            .map(participantId -> new ChatPresenceParticipantResponse(
                participantId,
                participantNameById.getOrDefault(participantId, fallbackParticipantName(participantId))
            ))
            .sorted(Comparator
                .comparing(ChatPresenceParticipantResponse::displayName)
                .thenComparing(ChatPresenceParticipantResponse::participantId))
            .toList();

        return new ChatPresenceResponse(participants.size(), participants);
    }

    public ChatPresenceResponse rememberParticipantName(String participantId, String displayName) {
        String normalizedParticipantId = normalizeParticipantId(participantId);
        if (normalizedParticipantId == null) {
            return getPresence();
        }

        String nextDisplayName = normalizeDisplayName(displayName, normalizedParticipantId);
        String previousDisplayName = participantNameById.put(normalizedParticipantId, nextDisplayName);
        boolean isActiveParticipant = activeParticipantCounts.containsKey(normalizedParticipantId);

        if (isActiveParticipant && !nextDisplayName.equals(previousDisplayName)) {
            publishPresence();
        }

        return getPresence();
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

        String nextParticipantId = normalizeParticipantId(participantId);
        if (nextParticipantId == null) {
            return;
        }
        participantIdBySessionId.put(sessionId, nextParticipantId);
        activeParticipantCounts.merge(nextParticipantId, 1, Integer::sum);
        participantNameById.putIfAbsent(nextParticipantId, fallbackParticipantName(nextParticipantId));
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
        if (!activeParticipantCounts.containsKey(participantId)) {
            participantNameById.remove(participantId);
        }
        publishPresence();
    }

    private String normalizeParticipantId(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return null;
        }

        return participantId.trim();
    }

    private String normalizeDisplayName(String displayName, String participantId) {
        if (!StringUtils.hasText(displayName)) {
            return fallbackParticipantName(participantId);
        }

        String normalizedName = displayName.trim().replaceAll("\\s+", " ");
        if ("익명".equals(normalizedName)) {
            return fallbackParticipantName(participantId);
        }

        return normalizedName.length() > 30 ? normalizedName.substring(0, 30) : normalizedName;
    }

    private String fallbackParticipantName(String participantId) {
        String normalizedParticipantId = normalizeParticipantId(participantId);
        if (normalizedParticipantId == null) {
            return "익명";
        }

        int suffixStart = Math.max(0, normalizedParticipantId.length() - 4);
        return "익명 #" + normalizedParticipantId.substring(suffixStart);
    }

    private void publishPresence() {
        messagingTemplate.convertAndSend(COMMENTS_PRESENCE_TOPIC, getPresence());
    }
}
