package com.yongsoo.youtubeatlasbackend.playback;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaybackProgressRepository extends JpaRepository<PlaybackProgress, Long> {

    Optional<PlaybackProgress> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<PlaybackProgress> findByUserIdAndVideoId(Long userId, String videoId);

    List<PlaybackProgress> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);
}
