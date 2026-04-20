package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameNotificationRepository extends JpaRepository<GameNotification, Long> {

    Optional<GameNotification> findByUserIdAndEventKey(Long userId, String eventKey);

    Optional<GameNotification> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<GameNotification> findBySeasonIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        Long seasonId,
        Long userId,
        Pageable pageable
    );

    @Modifying
    @Query("""
        update GameNotification notification
        set notification.readAt = :readAt
        where notification.season.id = :seasonId
          and notification.user.id = :userId
          and notification.deletedAt is null
          and notification.readAt is null
    """)
    int markSeasonNotificationsRead(Long seasonId, Long userId, java.time.Instant readAt);

    @Modifying
    @Query("""
        update GameNotification notification
        set notification.deletedAt = :deletedAt
        where notification.season.id = :seasonId
          and notification.user.id = :userId
          and notification.deletedAt is null
    """)
    int deleteSeasonNotifications(Long seasonId, Long userId, java.time.Instant deletedAt);

    void deleteByUserId(Long userId);
}
