package com.yongsoo.youtubeatlasbackend.comments;

public record CommentHighlight(
    String id,
    String authorName,
    String text,
    long likeCount
) {
}
