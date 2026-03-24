package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByVideoIdOrderByCreatedAtAsc(String videoId);

    Optional<Comment> findTopByVideoIdAndClientIdOrderByCreatedAtDesc(String videoId, String clientId);

    boolean existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
        String videoId,
        String clientId,
        String content,
        Instant createdAt
    );
}
