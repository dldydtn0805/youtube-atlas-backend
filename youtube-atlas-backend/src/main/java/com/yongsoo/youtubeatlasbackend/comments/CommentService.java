package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.api.ChatMessageResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.CreateCommentRequest;

@Service
public class CommentService {

    public static final String GLOBAL_ROOM_VIDEO_ID = "global";
    public static final String SYSTEM_MESSAGE_TYPE = "SYSTEM";
    public static final String USER_MESSAGE_TYPE = "USER";
    private static final String GLOBAL_COMMENTS_TOPIC = "/topic/comments";
    private static final String SYSTEM_AUTHOR = "시스템";
    private static final String SYSTEM_CLIENT_ID_PREFIX = "system:";
    private static final String TRADE_SYSTEM_CLIENT_ID = SYSTEM_CLIENT_ID_PREFIX + "trade";
    private static final String LOGIN_SYSTEM_CLIENT_ID = SYSTEM_CLIENT_ID_PREFIX + "login";
    private static final String TIER_SYSTEM_CLIENT_ID = SYSTEM_CLIENT_ID_PREFIX + "tier";

    static final Duration COMMENT_COOLDOWN = Duration.ofSeconds(5);
    static final Duration COMMENT_DUPLICATE_WINDOW = Duration.ofSeconds(30);
    static final Duration SYSTEM_MESSAGE_DUPLICATE_WINDOW = Duration.ofSeconds(10);

    private final CommentRepository commentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public CommentService(
        CommentRepository commentRepository,
        SimpMessagingTemplate messagingTemplate,
        Clock clock
    ) {
        this.commentRepository = commentRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getComments(String videoId) {
        return getComments((Instant) null);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getComments(String videoId, Instant since) {
        return getComments(since);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getComments(Instant since) {
        List<Comment> comments = since == null
            ? findLatestGlobalComments()
            : commentRepository.findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(GLOBAL_ROOM_VIDEO_ID, since);

        return comments.stream()
            .map(this::toResponse)
            .toList();
    }

    private List<Comment> findLatestGlobalComments() {
        List<Comment> comments = new ArrayList<>(
            commentRepository.findTop20ByVideoIdOrderByCreatedAtDesc(GLOBAL_ROOM_VIDEO_ID)
        );
        comments.sort((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()));
        return comments;
    }

    @Transactional
    public ChatMessageResponse createComment(String videoId, CreateCommentRequest request) {
        return createComment(request, null);
    }

    @Transactional
    public ChatMessageResponse createComment(String videoId, CreateCommentRequest request, AuthenticatedUser authenticatedUser) {
        return createComment(request, authenticatedUser);
    }

    @Transactional
    public ChatMessageResponse createComment(CreateCommentRequest request) {
        return createComment(request, null);
    }

    @Transactional
    public ChatMessageResponse createComment(CreateCommentRequest request, AuthenticatedUser authenticatedUser) {
        String clientId = normalizeRequired(request.clientId(), "clientId는 필수입니다.");
        if (clientId.startsWith(SYSTEM_CLIENT_ID_PREFIX)) {
            throw new CommentValidationException("사용할 수 없는 clientId입니다.");
        }

        String content = normalizeContent(request.content());
        String author = resolveAuthor(request.author(), authenticatedUser);
        Instant now = Instant.now(clock);

        commentRepository.findTopByVideoIdAndClientIdOrderByCreatedAtDesc(GLOBAL_ROOM_VIDEO_ID, clientId)
            .ifPresent(latestComment -> {
                long elapsedSeconds = Duration.between(latestComment.getCreatedAt(), now).getSeconds();

                if (elapsedSeconds < COMMENT_COOLDOWN.getSeconds()) {
                    int retryAfterSeconds = (int) Math.max(1L, COMMENT_COOLDOWN.getSeconds() - elapsedSeconds);
                    throw new CommentPolicyViolationException(
                        "cooldown",
                        "채팅 흐름을 위해 " + retryAfterSeconds + "초 후에 다시 보낼 수 있어요.",
                        retryAfterSeconds
                    );
                }
            });

        Instant duplicateWindowStart = now.minus(COMMENT_DUPLICATE_WINDOW);
        boolean hasDuplicate = commentRepository.existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
            GLOBAL_ROOM_VIDEO_ID,
            clientId,
            content,
            duplicateWindowStart
        );

        if (hasDuplicate) {
            throw new CommentPolicyViolationException(
                "duplicate",
                "같은 메시지는 30초 후에 다시 보낼 수 있어요.",
                null
            );
        }

        Comment comment = new Comment();
        comment.setVideoId(GLOBAL_ROOM_VIDEO_ID);
        comment.setClientId(clientId);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(now);

        ChatMessageResponse response = toResponse(commentRepository.save(comment));
        messagingTemplate.convertAndSend(GLOBAL_COMMENTS_TOPIC, response);
        return response;
    }

    @Transactional
    public ChatMessageResponse publishTradeSystemMessage(String content) {
        return publishSystemMessage(TRADE_SYSTEM_CLIENT_ID, content);
    }

    @Transactional
    public ChatMessageResponse publishLoginSystemMessage(String content) {
        return publishSystemMessage(LOGIN_SYSTEM_CLIENT_ID, content);
    }

    @Transactional
    public ChatMessageResponse publishTierSystemMessage(String content) {
        return publishSystemMessage(TIER_SYSTEM_CLIENT_ID, content);
    }

    private ChatMessageResponse publishSystemMessage(String clientId, String content) {
        String normalizedContent = normalizeContent(content);
        Instant now = Instant.now(clock);
        Instant duplicateWindowStart = now.minus(SYSTEM_MESSAGE_DUPLICATE_WINDOW);

        ChatMessageResponse duplicateResponse = commentRepository
            .findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
                GLOBAL_ROOM_VIDEO_ID,
                clientId,
                normalizedContent,
                duplicateWindowStart
            )
            .map(this::toResponse)
            .orElse(null);

        if (duplicateResponse != null) {
            return duplicateResponse;
        }

        Comment comment = new Comment();
        comment.setVideoId(GLOBAL_ROOM_VIDEO_ID);
        comment.setClientId(clientId);
        comment.setAuthor(SYSTEM_AUTHOR);
        comment.setContent(normalizedContent);
        comment.setCreatedAt(now);

        ChatMessageResponse response = toResponse(commentRepository.save(comment));
        messagingTemplate.convertAndSend(GLOBAL_COMMENTS_TOPIC, response);
        return response;
    }

    private String resolveAuthor(String requestedAuthor, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser != null && StringUtils.hasText(authenticatedUser.displayName())) {
            return authenticatedUser.displayName().trim();
        }

        return StringUtils.hasText(requestedAuthor) ? requestedAuthor.trim() : "익명";
    }

    private ChatMessageResponse toResponse(Comment comment) {
        return new ChatMessageResponse(
            comment.getId(),
            comment.getVideoId(),
            resolveMessageType(comment),
            comment.getAuthor(),
            comment.getContent(),
            comment.getClientId(),
            comment.getCreatedAt()
        );
    }

    private String resolveMessageType(Comment comment) {
        return comment.getClientId() != null && comment.getClientId().startsWith(SYSTEM_CLIENT_ID_PREFIX)
            ? SYSTEM_MESSAGE_TYPE
            : USER_MESSAGE_TYPE;
    }

    private String normalizeContent(String content) {
        String normalized = normalizeRequired(content, "메시지 내용을 입력해 주세요.")
            .replaceAll("\\s+", " ");

        if (!StringUtils.hasText(normalized)) {
            throw new CommentValidationException("메시지 내용을 입력해 주세요.");
        }

        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new CommentValidationException(message);
        }

        return value.trim();
    }
}
