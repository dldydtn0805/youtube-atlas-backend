package com.yongsoo.youtubeatlasbackend.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasCommentHighlight;

class CommentHighlightServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-03T10:00:00Z");

    private CommentHighlightFetchRepository fetchRepository;
    private StoredCommentHighlightRepository highlightRepository;
    private YouTubeApiClient youTubeApiClient;
    private CommentHighlightService commentHighlightService;

    @BeforeEach
    void setUp() {
        fetchRepository = org.mockito.Mockito.mock(CommentHighlightFetchRepository.class);
        highlightRepository = org.mockito.Mockito.mock(StoredCommentHighlightRepository.class);
        youTubeApiClient = org.mockito.Mockito.mock(YouTubeApiClient.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        commentHighlightService = new CommentHighlightService(
            fetchRepository,
            highlightRepository,
            youTubeApiClient,
            clock
        );
    }

    @Test
    void getHighlightsFetchesNormalizesSortsAndStoresByVideoId() {
        when(fetchRepository.findByVideoIdAndExpiresAtAfter(eq("video-1"), any()))
            .thenReturn(Optional.empty());
        when(youTubeApiClient.fetchTopLevelCommentHighlights("video-1")).thenReturn(List.of(
            comment("comment-1", "  Atlas   User  ", " 첫 번째   댓글 ", 2L),
            comment("comment-2", "Viewer", "좋아요 많은 댓글", 15L),
            comment("comment-3", "", "", 99L)
        ));

        List<CommentHighlight> highlights = commentHighlightService.getHighlights(" video-1 ");

        assertThat(highlights).extracting(CommentHighlight::id).containsExactly("comment-2", "comment-1");
        assertThat(highlights.get(1).authorName()).isEqualTo("Atlas User");
        assertThat(highlights.get(1).text()).isEqualTo("첫 번째 댓글");
        verify(youTubeApiClient, times(1)).fetchTopLevelCommentHighlights("video-1");
        verify(highlightRepository).deleteByVideoId("video-1");
        verify(fetchRepository).save(any(CommentHighlightFetch.class));
        verify(highlightRepository).saveAll(any());
    }

    @Test
    void getHighlightsUsesStoredHighlightsWhenFetchIsFresh() {
        when(fetchRepository.findByVideoIdAndExpiresAtAfter(eq("video-1"), any()))
            .thenReturn(Optional.of(fetch("video-1")));
        when(highlightRepository.findByVideoIdAndExpiresAtAfterOrderByLikeCountDesc(eq("video-1"), any()))
            .thenReturn(List.of(stored("comment-1", "Viewer", "저장된 댓글", 7L)));

        List<CommentHighlight> highlights = commentHighlightService.getHighlights("video-1");

        assertThat(highlights).extracting(CommentHighlight::id).containsExactly("comment-1");
        assertThat(highlights.getFirst().text()).isEqualTo("저장된 댓글");
        verify(youTubeApiClient, times(0)).fetchTopLevelCommentHighlights("video-1");
    }

    @Test
    void getHighlightsRejectsBlankVideoId() {
        assertThatThrownBy(() -> commentHighlightService.getHighlights(" "))
            .isInstanceOf(CommentValidationException.class)
            .hasMessage("videoId는 필수입니다.");
    }

    private AtlasCommentHighlight comment(String id, String authorName, String text, Long likeCount) {
        return new AtlasCommentHighlight(
            id,
            authorName,
            text,
            likeCount,
            OffsetDateTime.parse("2026-05-03T10:00:00Z")
        );
    }

    private CommentHighlightFetch fetch(String videoId) {
        CommentHighlightFetch fetch = new CommentHighlightFetch();
        fetch.setVideoId(videoId);
        fetch.setFetchedAt(NOW);
        fetch.setExpiresAt(NOW.plusSeconds(3600));
        return fetch;
    }

    private StoredCommentHighlight stored(String id, String authorName, String text, Long likeCount) {
        StoredCommentHighlight comment = new StoredCommentHighlight();
        comment.setVideoId("video-1");
        comment.setCommentId(id);
        comment.setAuthorName(authorName);
        comment.setText(text);
        comment.setLikeCount(likeCount);
        comment.setFetchedAt(NOW);
        comment.setExpiresAt(NOW.plusSeconds(3600));
        return comment;
    }
}
