package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasCommentHighlight;

@Service
public class CommentHighlightService {

    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final long MAX_CACHED_VIDEOS = 500;
    private static final int MAX_COMMENT_TEXT_LENGTH = 240;
    private static final String FALLBACK_AUTHOR = "YouTube 댓글";

    private final Cache<String, List<CommentHighlight>> highlightsByVideoId = Caffeine.newBuilder()
        .maximumSize(MAX_CACHED_VIDEOS)
        .expireAfterWrite(CACHE_TTL)
        .build();
    private final YouTubeApiClient youTubeApiClient;

    public CommentHighlightService(YouTubeApiClient youTubeApiClient) {
        this.youTubeApiClient = youTubeApiClient;
    }

    public List<CommentHighlight> getHighlights(String videoId) {
        String normalizedVideoId = normalizeVideoId(videoId);
        return highlightsByVideoId.get(normalizedVideoId, this::loadHighlights);
    }

    private List<CommentHighlight> loadHighlights(String videoId) {
        return youTubeApiClient.fetchTopLevelCommentHighlights(videoId).stream()
            .map(this::toHighlight)
            .filter(comment -> StringUtils.hasText(comment.id()) && StringUtils.hasText(comment.text()))
            .sorted(Comparator.comparingLong(CommentHighlight::likeCount).reversed())
            .toList();
    }

    private CommentHighlight toHighlight(AtlasCommentHighlight comment) {
        return new CommentHighlight(
            comment.id(),
            normalizeAuthorName(comment.authorName()),
            normalizeText(comment.text()),
            comment.likeCount() != null ? comment.likeCount() : 0L
        );
    }

    private String normalizeVideoId(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            throw new CommentValidationException("videoId는 필수입니다.");
        }

        return videoId.trim();
    }

    private String normalizeAuthorName(String authorName) {
        if (!StringUtils.hasText(authorName)) {
            return FALLBACK_AUTHOR;
        }

        String normalized = authorName.trim().replaceAll("\\s+", " ");
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() > MAX_COMMENT_TEXT_LENGTH
            ? normalized.substring(0, MAX_COMMENT_TEXT_LENGTH)
            : normalized;
    }
}
