package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findTop8ByOrderByCreatedAtDesc();

    List<Comment> findTop8ByVideoIdOrderByCreatedAtDesc(String videoId);

    List<Comment> findTop20ByVideoIdOrderByCreatedAtDesc(String videoId);

    long countByVideoId(String videoId);

    List<Comment> findByVideoIdOrderByCreatedAtAsc(String videoId);

    List<Comment> findByVideoIdAndCreatedAtAfterOrderByCreatedAtAsc(String videoId, Instant createdAt);

    long deleteByCreatedAtBefore(Instant createdAt);

    long deleteByVideoIdAndCreatedAtBefore(String videoId, Instant createdAt);

    Optional<Comment> findTopByVideoIdAndClientIdOrderByCreatedAtDesc(String videoId, String clientId);

    Optional<Comment> findTopByVideoIdAndClientIdAndContentAndCreatedAtAfterOrderByCreatedAtDesc(
        String videoId,
        String clientId,
        String content,
        Instant createdAt
    );

    boolean existsByVideoIdAndClientIdAndContentAndCreatedAtAfter(
        String videoId,
        String clientId,
        String content,
        Instant createdAt
    );
}
