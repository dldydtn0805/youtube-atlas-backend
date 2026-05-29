package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentHighlightFetchRepository extends JpaRepository<CommentHighlightFetch, String> {

    Optional<CommentHighlightFetch> findByVideoIdAndExpiresAtAfter(String videoId, Instant now);

    long deleteByExpiresAtBefore(Instant now);
}
