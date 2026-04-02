package com.yongsoo.youtubeatlasbackend.playback;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaybackProgressRepository extends JpaRepository<PlaybackProgress, Long> {

    Optional<PlaybackProgress> findByUserId(Long userId);
}
