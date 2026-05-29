package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredCommentHighlightRepository extends JpaRepository<StoredCommentHighlight, Long> {

    List<StoredCommentHighlight> findByVideoIdAndExpiresAtAfterOrderByLikeCountDesc(
        String videoId,
        Instant now
    );

    long deleteByVideoId(String videoId);

    long deleteByExpiresAtBefore(Instant now);
}
