package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminCommentCleanupRequest;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminCommentCleanupResponse;
import com.yongsoo.youtubeatlasbackend.comments.CommentRepository;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;

@Service
public class AdminCommentService {

    private final CommentRepository commentRepository;
    private final Clock clock;

    public AdminCommentService(CommentRepository commentRepository, Clock clock) {
        this.commentRepository = commentRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminCommentCleanupResponse deleteCommentsOlderThan(AdminCommentCleanupRequest request) {
        Instant deleteBefore = request.deleteBefore();
        Long userId = request.userId();
        Instant now = Instant.now(clock);

        if (deleteBefore.isAfter(now)) {
            throw new IllegalArgumentException("deleteBefore는 현재 시각 이전이어야 합니다.");
        }

        long deletedCount = userId == null
            ? commentRepository.deleteByVideoIdAndCreatedAtBefore(
                CommentService.GLOBAL_ROOM_VIDEO_ID,
                deleteBefore
            )
            : commentRepository.deleteByVideoIdAndUserIdAndCreatedAtBefore(
                CommentService.GLOBAL_ROOM_VIDEO_ID,
                userId,
                deleteBefore
            );
        return new AdminCommentCleanupResponse(deleteBefore, now, deletedCount);
    }
}
