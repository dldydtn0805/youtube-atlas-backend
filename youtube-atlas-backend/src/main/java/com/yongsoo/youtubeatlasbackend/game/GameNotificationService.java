package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;

@Service
public class GameNotificationService {

    private static final String USER_GAME_NOTIFICATIONS_QUEUE = "/queue/game/notifications";
    private static final int NOTIFICATION_LIMIT = 20;

    private final GameNotificationRepository gameNotificationRepository;
    private final AppUserRepository appUserRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public GameNotificationService(
        GameNotificationRepository gameNotificationRepository,
        AppUserRepository appUserRepository,
        SimpMessagingTemplate messagingTemplate,
        Clock clock
    ) {
        this.gameNotificationRepository = gameNotificationRepository;
        this.appUserRepository = appUserRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @Transactional
    public List<GameNotificationResponse> syncAndListSeasonNotifications(
        GameSeason season,
        Long userId,
        List<GameNotificationResponse> generatedNotifications
    ) {
        AppUser user = appUserRepository.getReferenceById(userId);
        generatedNotifications.forEach(notification -> saveIfAbsent(user, season, notification));
        return listSeasonNotifications(season.getId(), userId);
    }

    @Transactional(readOnly = true)
    public List<GameNotificationResponse> listSeasonNotifications(Long seasonId, Long userId) {
        return gameNotificationRepository.findBySeasonIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                seasonId,
                userId,
                PageRequest.of(0, NOTIFICATION_LIMIT)
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public List<GameNotificationResponse> createAndPushPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentValuePoints,
        Instant capturedAt
    ) {
        List<GameNotificationResponse> notifications = GameNotificationFactory.fromPositionSnapshot(
            position,
            currentRank,
            currentValuePoints,
            capturedAt
        );
        return createAndPush(position.getUser(), position.getSeason(), notifications);
    }

    @Transactional
    public List<GameNotificationResponse> createAndPush(
        AppUser user,
        GameSeason season,
        List<GameNotificationResponse> notifications
    ) {
        return notifications.stream()
            .map(notification -> saveIfAbsentResult(user, season, notification))
            .filter(savedNotification -> savedNotification.created())
            .filter(savedNotification -> savedNotification.notification().getDeletedAt() == null)
            .map(savedNotification -> toResponse(savedNotification.notification()))
            .peek(notification -> messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                USER_GAME_NOTIFICATIONS_QUEUE,
                notification
            ))
            .toList();
    }

    @Transactional
    public void markSeasonNotificationsRead(Long seasonId, Long userId) {
        gameNotificationRepository.markSeasonNotificationsRead(seasonId, userId, Instant.now(clock));
    }

    @Transactional
    public void deleteSeasonNotifications(Long seasonId, Long userId) {
        gameNotificationRepository.deleteSeasonNotifications(seasonId, userId, Instant.now(clock));
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        gameNotificationRepository.findByIdAndUserIdAndDeletedAtIsNull(notificationId, userId)
            .ifPresent(notification -> {
                notification.setDeletedAt(Instant.now(clock));
                gameNotificationRepository.save(notification);
            });
    }

    private GameNotification saveIfAbsent(
        AppUser user,
        GameSeason season,
        GameNotificationResponse response
    ) {
        return saveIfAbsentResult(user, season, response).notification();
    }

    private SavedNotification saveIfAbsentResult(
        AppUser user,
        GameSeason season,
        GameNotificationResponse response
    ) {
        return gameNotificationRepository.findByUserIdAndEventKey(user.getId(), response.id())
            .map(notification -> new SavedNotification(notification, false))
            .orElseGet(() -> {
                try {
                    return new SavedNotification(gameNotificationRepository.save(toEntity(user, season, response)), true);
                } catch (DataIntegrityViolationException ignored) {
                    return new SavedNotification(
                        gameNotificationRepository.findByUserIdAndEventKey(user.getId(), response.id()).orElseThrow(),
                        false
                    );
                }
            });
    }

    private GameNotification toEntity(AppUser user, GameSeason season, GameNotificationResponse response) {
        GameNotification notification = new GameNotification();
        notification.setUser(user);
        notification.setSeason(season);
        notification.setEventKey(response.id());
        notification.setNotificationType(response.notificationType());
        notification.setTitle(response.title());
        notification.setMessage(response.message());
        notification.setPositionId(response.positionId());
        notification.setVideoId(response.videoId());
        notification.setVideoTitle(response.videoTitle());
        notification.setChannelTitle(response.channelTitle());
        notification.setThumbnailUrl(response.thumbnailUrl());
        notification.setStrategyTags(joinStrategyTags(response.strategyTags()));
        notification.setHighlightScore(response.highlightScore());
        notification.setCreatedAt(response.createdAt() != null ? response.createdAt() : Instant.now(clock));
        return notification;
    }

    private GameNotificationResponse toResponse(GameNotification notification) {
        return new GameNotificationResponse(
            notification.getId().toString(),
            notification.getNotificationType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getPositionId(),
            notification.getVideoId(),
            notification.getVideoTitle(),
            notification.getChannelTitle(),
            notification.getThumbnailUrl(),
            parseStrategyTags(notification.getStrategyTags()),
            notification.getHighlightScore(),
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }

    private String joinStrategyTags(List<GameStrategyType> strategyTags) {
        if (strategyTags == null || strategyTags.isEmpty()) {
            return "";
        }

        return strategyTags.stream()
            .map(GameStrategyType::name)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private List<GameStrategyType> parseStrategyTags(String strategyTags) {
        if (strategyTags == null || strategyTags.isBlank()) {
            return List.of();
        }

        return Arrays.stream(strategyTags.split(","))
            .filter(value -> !value.isBlank())
            .map(GameStrategyType::valueOf)
            .toList();
    }

    private record SavedNotification(GameNotification notification, boolean created) {
    }
}
