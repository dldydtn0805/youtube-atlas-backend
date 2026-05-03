package com.yongsoo.youtubeatlasbackend.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasCommentHighlight;

class CommentHighlightServiceTest {

    private YouTubeApiClient youTubeApiClient;
    private CommentHighlightService commentHighlightService;

    @BeforeEach
    void setUp() {
        youTubeApiClient = org.mockito.Mockito.mock(YouTubeApiClient.class);
        commentHighlightService = new CommentHighlightService(youTubeApiClient);
    }

    @Test
    void getHighlightsFetchesNormalizesSortsAndCachesByVideoId() {
        when(youTubeApiClient.fetchTopLevelCommentHighlights("video-1")).thenReturn(List.of(
            comment("comment-1", "  Atlas   User  ", " 첫 번째   댓글 ", 2L),
            comment("comment-2", "Viewer", "좋아요 많은 댓글", 15L),
            comment("comment-3", "", "", 99L)
        ));

        List<CommentHighlight> first = commentHighlightService.getHighlights(" video-1 ");
        List<CommentHighlight> second = commentHighlightService.getHighlights("video-1");

        assertThat(first).extracting(CommentHighlight::id).containsExactly("comment-2", "comment-1");
        assertThat(first.get(1).authorName()).isEqualTo("Atlas User");
        assertThat(first.get(1).text()).isEqualTo("첫 번째 댓글");
        assertThat(second).isSameAs(first);
        verify(youTubeApiClient, times(1)).fetchTopLevelCommentHighlights("video-1");
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
}
