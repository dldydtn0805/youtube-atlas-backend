package com.yongsoo.youtubeatlasbackend.comments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.common.ExternalServiceException;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasCommentHighlight;

@Service
public class CommentHighlightService {

    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int MAX_COMMENT_TEXT_LENGTH = 240;
    private static final String FALLBACK_AUTHOR = "YouTube 댓글";

    private final CommentHighlightFetchRepository fetchRepository;
    private final StoredCommentHighlightRepository highlightRepository;
    private final YouTubeApiClient youTubeApiClient;
    private final Clock clock;

    public CommentHighlightService(
        CommentHighlightFetchRepository fetchRepository,
        StoredCommentHighlightRepository highlightRepository,
        YouTubeApiClient youTubeApiClient,
        Clock clock
    ) {
        this.fetchRepository = fetchRepository;
        this.highlightRepository = highlightRepository;
        this.youTubeApiClient = youTubeApiClient;
        this.clock = clock;
    }

    public List<CommentHighlight> getHighlights(String videoId) {
        String normalizedVideoId = normalizeVideoId(videoId);
        Instant now = Instant.now(clock);
        if (fetchRepository.findByVideoIdAndExpiresAtAfter(normalizedVideoId, now).isPresent()) {
            return highlightRepository
                .findByVideoIdAndExpiresAtAfterOrderByLikeCountDesc(normalizedVideoId, now)
                .stream()
                .map(this::toHighlight)
                .toList();
        }

        return refreshHighlights(normalizedVideoId, now);
    }

    @Transactional
    public long purgeExpiredHighlights() {
        Instant now = Instant.now(clock);
        long deletedHighlights = highlightRepository.deleteByExpiresAtBefore(now);
        fetchRepository.deleteByExpiresAtBefore(now);
        return deletedHighlights;
    }

    @Transactional
    protected List<CommentHighlight> refreshHighlights(String videoId, Instant fetchedAt) {
        List<CommentHighlight> highlights = loadHighlights(videoId);
        Instant expiresAt = fetchedAt.plus(CACHE_TTL);

        highlightRepository.deleteByVideoId(videoId);
        fetchRepository.save(fetchRecord(videoId, fetchedAt, expiresAt));
        highlightRepository.saveAll(highlights.stream()
            .map(highlight -> storedHighlight(videoId, highlight, fetchedAt, expiresAt))
            .toList());

        return highlights;
    }

    private List<CommentHighlight> loadHighlights(String videoId) {
        try {
            return youTubeApiClient.fetchTopLevelCommentHighlights(videoId).stream()
                .map(this::toHighlight)
                .filter(comment -> StringUtils.hasText(comment.id()) && StringUtils.hasText(comment.text()))
                .sorted(Comparator.comparingLong(CommentHighlight::likeCount).reversed())
                .toList();
        } catch (ExternalServiceException exception) {
            if (isCommentsDisabledError(exception)) {
                return List.of();
            }

            throw exception;
        }
    }

    private CommentHighlight toHighlight(StoredCommentHighlight comment) {
        return new CommentHighlight(
            comment.getCommentId(),
            comment.getAuthorName(),
            comment.getText(),
            comment.getLikeCount()
        );
    }

    private boolean isCommentsDisabledError(ExternalServiceException exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message)
            && message.toLowerCase().contains("disabled comments");
    }

    private CommentHighlight toHighlight(AtlasCommentHighlight comment) {
        return new CommentHighlight(
            comment.id(),
            normalizeAuthorName(comment.authorName()),
            normalizeText(comment.text()),
            comment.likeCount() != null ? comment.likeCount() : 0L
        );
    }

    private CommentHighlightFetch fetchRecord(String videoId, Instant fetchedAt, Instant expiresAt) {
        CommentHighlightFetch fetch = new CommentHighlightFetch();
        fetch.setVideoId(videoId);
        fetch.setFetchedAt(fetchedAt);
        fetch.setExpiresAt(expiresAt);
        return fetch;
    }

    private StoredCommentHighlight storedHighlight(
        String videoId,
        CommentHighlight highlight,
        Instant fetchedAt,
        Instant expiresAt
    ) {
        StoredCommentHighlight storedHighlight = new StoredCommentHighlight();
        storedHighlight.setVideoId(videoId);
        storedHighlight.setCommentId(highlight.id());
        storedHighlight.setAuthorName(highlight.authorName());
        storedHighlight.setText(highlight.text());
        storedHighlight.setLikeCount(highlight.likeCount());
        storedHighlight.setFetchedAt(fetchedAt);
        storedHighlight.setExpiresAt(expiresAt);
        return storedHighlight;
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
