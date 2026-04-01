package com.yongsoo.youtubeatlasbackend.favorites;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteStreamerRepository extends JpaRepository<FavoriteStreamer, Long> {

    List<FavoriteStreamer> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteStreamer> findByUserIdAndChannelId(Long userId, String channelId);
}
