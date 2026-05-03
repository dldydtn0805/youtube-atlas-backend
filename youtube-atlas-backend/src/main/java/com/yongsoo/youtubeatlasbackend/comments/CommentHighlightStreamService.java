package com.yongsoo.youtubeatlasbackend.comments;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.yongsoo.youtubeatlasbackend.auth.AuthException;
import com.yongsoo.youtubeatlasbackend.comments.api.CommentHighlightResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.CommentHighlightStartRequest;

@Service
public class CommentHighlightStreamService {

    public static final String USER_COMMENT_HIGHLIGHTS_QUEUE = "/queue/comments/highlights";
    private static final Logger log = LoggerFactory.getLogger(CommentHighlightStreamService.class);
    private static final int MIN_DELAY_SECONDS = 5;
    private static final int MAX_DELAY_SECONDS = 10;
    private static final int MAX_EMISSIONS_PER_STREAM = 100;

    private final Map<String, ActiveCommentHighlightStream> activeStreamsBySessionId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final CommentHighlightService commentHighlightService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public CommentHighlightStreamService(
        CommentHighlightService commentHighlightService,
        SimpMessagingTemplate messagingTemplate,
        Clock clock
    ) {
        this.commentHighlightService = commentHighlightService;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void start(CommentHighlightStartRequest request, Principal principal, String sessionId) {
        String userName = requireUserName(principal);
        String normalizedSessionId = normalizeSessionId(sessionId);
        String videoId = normalizeVideoId(request != null ? request.videoId() : null);
        stopSession(normalizedSessionId);

        List<CommentHighlight> highlights = commentHighlightService.getHighlights(videoId);
        if (highlights.isEmpty()) {
            return;
        }

        ActiveCommentHighlightStream stream = new ActiveCommentHighlightStream(
            normalizedSessionId,
            userName,
            videoId,
            highlights.stream().limit(MAX_EMISSIONS_PER_STREAM).toList()
        );
        activeStreamsBySessionId.put(normalizedSessionId, stream);
        emitNext(stream);
    }

    public void stop(Principal principal, String sessionId) {
        requireUserName(principal);
        stopSession(normalizeSessionId(sessionId));
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        stopSession(event.getSessionId());
    }

    @PreDestroy
    public void shutdown() {
        activeStreamsBySessionId.values().forEach(ActiveCommentHighlightStream::cancel);
        activeStreamsBySessionId.clear();
        scheduler.shutdownNow();
    }

    private void emitNext(ActiveCommentHighlightStream stream) {
        try {
            ActiveCommentHighlightStream current = activeStreamsBySessionId.get(stream.sessionId());
            if (current != stream) {
                stream.cancel();
                return;
            }

            int index = stream.nextIndex();
            if (index >= stream.highlights().size()) {
                stopSession(stream.sessionId());
                return;
            }

            CommentHighlightResponse response = CommentHighlightResponse.from(
                stream.videoId(),
                stream.highlights().get(index),
                Instant.now(clock)
            );
            messagingTemplate.convertAndSendToUser(
                stream.userName(),
                USER_COMMENT_HIGHLIGHTS_QUEUE,
                response,
                sessionHeaders(stream.sessionId())
            );

            if (index + 1 >= stream.highlights().size()) {
                stopSession(stream.sessionId());
                return;
            }
            scheduleNext(stream);
        } catch (RuntimeException exception) {
            log.warn("Failed to emit comment highlight for session {}", stream.sessionId(), exception);
            stopSession(stream.sessionId());
        }
    }

    private void scheduleNext(ActiveCommentHighlightStream stream) {
        if (activeStreamsBySessionId.get(stream.sessionId()) != stream) {
            return;
        }

        ScheduledFuture<?> future = scheduler.schedule(
            () -> emitNext(stream),
            nextDelaySeconds(),
            TimeUnit.SECONDS
        );
        stream.setFuture(future);
    }

    private int nextDelaySeconds() {
        return ThreadLocalRandom.current().nextInt(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS + 1);
    }

    private void stopSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        ActiveCommentHighlightStream stream = activeStreamsBySessionId.remove(sessionId);
        if (stream != null) {
            stream.cancel();
        }
    }

    private MessageHeaders sessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }

    private String requireUserName(Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new AuthException("unauthorized", "로그인이 필요합니다.");
        }

        return principal.getName();
    }

    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new CommentValidationException("WebSocket sessionId가 필요합니다.");
        }

        return sessionId.trim();
    }

    private String normalizeVideoId(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            throw new CommentValidationException("videoId는 필수입니다.");
        }

        return videoId.trim();
    }

    private static final class ActiveCommentHighlightStream {

        private final String sessionId;
        private final String userName;
        private final String videoId;
        private final List<CommentHighlight> highlights;
        private final AtomicInteger nextIndex = new AtomicInteger();
        private volatile ScheduledFuture<?> future;

        private ActiveCommentHighlightStream(
            String sessionId,
            String userName,
            String videoId,
            List<CommentHighlight> highlights
        ) {
            this.sessionId = sessionId;
            this.userName = userName;
            this.videoId = videoId;
            this.highlights = highlights;
        }

        private String sessionId() {
            return sessionId;
        }

        private String userName() {
            return userName;
        }

        private String videoId() {
            return videoId;
        }

        private List<CommentHighlight> highlights() {
            return highlights;
        }

        private int nextIndex() {
            return nextIndex.getAndIncrement();
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private void cancel() {
            ScheduledFuture<?> currentFuture = future;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
        }
    }
}
