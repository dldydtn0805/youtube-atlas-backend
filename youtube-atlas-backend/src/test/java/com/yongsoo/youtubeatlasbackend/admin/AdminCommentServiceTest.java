package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminCommentCleanupRequest;
import com.yongsoo.youtubeatlasbackend.comments.CommentRepository;

class AdminCommentServiceTest {

    private CommentRepository commentRepository;
    private AdminCommentService adminCommentService;

    @BeforeEach
    void setUp() {
        commentRepository = org.mockito.Mockito.mock(CommentRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-15T03:00:00Z"), ZoneOffset.UTC);
        adminCommentService = new AdminCommentService(commentRepository, clock);
    }

    @Test
    void deleteCommentsOlderThanPurgesRowsAndReturnsSummary() {
        Instant deleteBefore = Instant.parse("2026-04-01T00:00:00Z");
        when(commentRepository.deleteByCreatedAtBefore(deleteBefore)).thenReturn(27L);

        var response = adminCommentService.deleteCommentsOlderThan(new AdminCommentCleanupRequest(deleteBefore));

        assertThat(response.deleteBefore()).isEqualTo(deleteBefore);
        assertThat(response.deletedAt()).isEqualTo(Instant.parse("2026-04-15T03:00:00Z"));
        assertThat(response.deletedCount()).isEqualTo(27L);
        verify(commentRepository).deleteByCreatedAtBefore(deleteBefore);
    }

    @Test
    void deleteCommentsOlderThanRejectsFutureTimestamp() {
        Instant future = Instant.parse("2026-04-16T00:00:00Z");

        assertThatThrownBy(() -> adminCommentService.deleteCommentsOlderThan(new AdminCommentCleanupRequest(future)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deleteBefore");
    }
}
