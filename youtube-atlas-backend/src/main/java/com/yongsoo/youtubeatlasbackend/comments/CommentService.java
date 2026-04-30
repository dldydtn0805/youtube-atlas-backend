package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AuthException;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.api.ChatMessageResponse;
import com.yongsoo.youtubeatlasbackend.comments.api.CreateCommentRequest;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightState;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightStateRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTier;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTierRepository;
import com.yongsoo.youtubeatlasbackend.game.GameTierService;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;

@Service
public class CommentService {

    public static final String GLOBAL_ROOM_VIDEO_ID = "global";
    public static final String SYSTEM_MESSAGE_TYPE = "SYSTEM";
    public static final String USER_MESSAGE_TYPE = "USER";
    public static final String TRADE_SYSTEM_EVENT_TYPE = "TRADE";
    public static final String LOGIN_SYSTEM_EVENT_TYPE = "LOGIN";
    public static final String TIER_SYSTEM_EVENT_TYPE = "TIER";
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
    private final CommentPresenceService commentPresenceService;
    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GameHighlightStateRepository gameHighlightStateRepository;
    private final GameSeasonTierRepository gameSeasonTierRepository;
    private final GameTierService gameTierService;
    private final Clock clock;

    public CommentService(
        CommentRepository commentRepository,
        SimpMessagingTemplate messagingTemplate,
        CommentPresenceService commentPresenceService,
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GameHighlightStateRepository gameHighlightStateRepository,
        GameSeasonTierRepository gameSeasonTierRepository,
        GameTierService gameTierService,
        Clock clock
    ) {
        this.commentRepository = commentRepository;
        this.messagingTemplate = messagingTemplate;
        this.commentPresenceService = commentPresenceService;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gameHighlightStateRepository = gameHighlightStateRepository;
        this.gameSeasonTierRepository = gameSeasonTierRepository;
        this.gameTierService = gameTierService;
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
    public List<ChatMessageResponse> getComments(String videoId, Instant since, String regionCode) {
        return getComments(since, regionCode);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getComments(Instant since) {
        return getComments(since, null);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getComments(Instant since, String regionCode) {
        List<Comment> comments = since == null
            ? findLatestGlobalComments()
            : commentRepository.findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(GLOBAL_ROOM_VIDEO_ID, since);
        Map<Long, String> tierCodesByUserId = new HashMap<>();

        return comments.stream()
            .map(comment -> toResponse(comment, regionCode, tierCodesByUserId))
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
        AuthenticatedUser user = requireAuthenticatedUser(authenticatedUser);
        String clientId = normalizeRequired(request.clientId(), "clientId는 필수입니다.");
        if (clientId.startsWith(SYSTEM_CLIENT_ID_PREFIX)) {
            throw new CommentValidationException("사용할 수 없는 clientId입니다.");
        }

        String content = normalizeContent(request.content());
        String author = resolveAuthor(request.author(), user);
        Long userId = user.id();
        Instant now = Instant.now(clock);

        findLatestUserComment(clientId, userId)
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
        boolean hasDuplicate = hasDuplicateUserComment(clientId, userId, content, duplicateWindowStart);

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
        comment.setUserId(userId);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(now);

        ChatMessageResponse response = toResponse(commentRepository.save(comment), request.regionCode());
        commentPresenceService.rememberParticipantName(clientId, author);
        messagingTemplate.convertAndSend(GLOBAL_COMMENTS_TOPIC, response);
        return response;
    }

    private AuthenticatedUser requireAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            throw new AuthException("unauthorized", "로그인이 필요합니다.");
        }

        return authenticatedUser;
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
        comment.setUserId(null);
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
        return toResponse(comment, null);
    }

    private ChatMessageResponse toResponse(Comment comment, String regionCode) {
        return toResponse(comment, regionCode, new HashMap<>());
    }

    private ChatMessageResponse toResponse(
        Comment comment,
        String regionCode,
        Map<Long, String> tierCodesByUserId
    ) {
        return new ChatMessageResponse(
            comment.getId(),
            comment.getVideoId(),
            resolveMessageType(comment),
            resolveSystemEventType(comment),
            comment.getAuthor(),
            comment.getContent(),
            comment.getClientId(),
            comment.getUserId(),
            resolveCurrentTierCode(comment.getUserId(), regionCode, tierCodesByUserId),
            comment.getCreatedAt()
        );
    }

    private String resolveCurrentTierCode(
        Long userId,
        String regionCode,
        Map<Long, String> tierCodesByUserId
    ) {
        if (userId == null) {
            return null;
        }
        if (!tierCodesByUserId.containsKey(userId)) {
            tierCodesByUserId.put(userId, resolveCurrentTierCode(userId, regionCode).orElse(null));
        }

        return tierCodesByUserId.get(userId);
    }

    private Optional<String> resolveCurrentTierCode(Long userId, String regionCode) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        if (!StringUtils.hasText(normalizedRegionCode)) {
            return Optional.empty();
        }

        GameSeason season = gameSeasonRepository
            .findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, normalizedRegionCode)
            .orElse(null);
        if (season == null) {
            return Optional.empty();
        }

        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserId(season.getId(), userId).orElse(null);
        if (wallet == null) {
            return Optional.empty();
        }

        List<GameSeasonTier> tiers = gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(season.getId());
        if (tiers == null || tiers.isEmpty()) {
            return Optional.empty();
        }

        long highlightScore = gameHighlightStateRepository
            .findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(
                season.getId(),
                userId,
                0L
            ).stream()
            .mapToLong(GameHighlightState::getBestSettledHighlightScore)
            .sum();
        long adjustedHighlightScore = highlightScore + normalizeTierScoreAdjustment(wallet.getManualTierScoreAdjustment());
        return Optional.ofNullable(gameTierService.resolveTier(tiers, adjustedHighlightScore).getTierCode());
    }

    private Optional<Comment> findLatestUserComment(String clientId, Long userId) {
        if (userId != null) {
            return commentRepository.findTopByVideoIdAndUserIdOrderByCreatedAtDesc(GLOBAL_ROOM_VIDEO_ID, userId);
        }

        return commentRepository.findTopByVideoIdAndClientIdOrderByCreatedAtDesc(GLOBAL_ROOM_VIDEO_ID, clientId);
    }

    private boolean hasDuplicateUserComment(
        String clientId,
        Long userId,
        String content,
        Instant duplicateWindowStart
    ) {
        if (userId != null) {
            return commentRepository.existsByVideoIdAndUserIdAndContentAndCreatedAtAfter(
                GLOBAL_ROOM_VIDEO_ID,
                userId,
                content,
                duplicateWindowStart
            );
        }

        return commentRepository.existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
            GLOBAL_ROOM_VIDEO_ID,
            clientId,
            content,
            duplicateWindowStart
        );
    }

    private String resolveMessageType(Comment comment) {
        return comment.getClientId() != null && comment.getClientId().startsWith(SYSTEM_CLIENT_ID_PREFIX)
            ? SYSTEM_MESSAGE_TYPE
            : USER_MESSAGE_TYPE;
    }

    private String resolveSystemEventType(Comment comment) {
        if (comment.getClientId() == null) {
            return null;
        }

        return switch (comment.getClientId()) {
            case TRADE_SYSTEM_CLIENT_ID -> TRADE_SYSTEM_EVENT_TYPE;
            case LOGIN_SYSTEM_CLIENT_ID -> LOGIN_SYSTEM_EVENT_TYPE;
            case TIER_SYSTEM_CLIENT_ID -> TIER_SYSTEM_EVENT_TYPE;
            default -> null;
        };
    }

    private String normalizeContent(String content) {
        String normalized = normalizeRequired(content, "메시지 내용을 입력해 주세요.")
            .replaceAll("\\s+", " ");

        if (!StringUtils.hasText(normalized)) {
            throw new CommentValidationException("메시지 내용을 입력해 주세요.");
        }

        return normalized;
    }

    private String normalizeRegionCode(String regionCode) {
        return StringUtils.hasText(regionCode) ? regionCode.trim().toUpperCase() : null;
    }

    private long normalizeTierScoreAdjustment(Long value) {
        return value != null ? value : 0L;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new CommentValidationException(message);
        }

        return value.trim();
    }
}
