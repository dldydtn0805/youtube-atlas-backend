package com.yongsoo.youtubeatlasbackend.comments.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.yongsoo.youtubeatlasbackend.comments.CommentHighlight;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CommentHighlightResponse(
    String id,
    String videoId,
    String messageType,
    String source,
    String label,
    String author,
    String content,
    String clientId,
    long likeCount,
    boolean ephemeral,
    Instant createdAt
) {
    private static final String MESSAGE_TYPE = "COMMENT_HIGHLIGHT";
    private static final String SOURCE = "YOUTUBE_COMMENT";
    private static final String LABEL = "인기 댓글";
    private static final String ID_PREFIX = "yt-comment:";

    public static CommentHighlightResponse from(String videoId, CommentHighlight comment, Instant createdAt) {
        String id = ID_PREFIX + comment.id();
        return new CommentHighlightResponse(
            id,
            videoId,
            MESSAGE_TYPE,
            SOURCE,
            LABEL,
            comment.authorName(),
            comment.text(),
            id,
            comment.likeCount(),
            true,
            createdAt
        );
    }
}
