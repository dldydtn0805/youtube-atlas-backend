package com.yongsoo.youtubeatlasbackend.comments.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.CommentHighlightService;
import com.yongsoo.youtubeatlasbackend.comments.CommentPresenceService;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

@RestController
public class CommentController {

    private static final int MAX_PUBLIC_COMMENT_HIGHLIGHTS = 100;

    private final CommentService commentService;
    private final CommentHighlightService commentHighlightService;
    private final CommentPresenceService commentPresenceService;
    private final AchievementTitleService achievementTitleService;
    private final AuthService authService;

    public CommentController(
        CommentService commentService,
        CommentHighlightService commentHighlightService,
        CommentPresenceService commentPresenceService,
        AchievementTitleService achievementTitleService,
        AuthService authService
    ) {
        this.commentService = commentService;
        this.commentHighlightService = commentHighlightService;
        this.commentPresenceService = commentPresenceService;
        this.achievementTitleService = achievementTitleService;
        this.authService = authService;
    }

    @GetMapping("/api/comments/presence")
    public ChatPresenceResponse getCommentPresence() {
        return commentPresenceService.getPresence();
    }

    @PostMapping("/api/comments/presence/me")
    public ChatPresenceResponse updateCommentPresence(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody UpdateCommentPresenceRequest request
    ) {
        AuthenticatedUser user = authService.requireCurrentUser(authorizationHeader);
        return commentPresenceService.rememberParticipantName(request.clientId(), user.displayName());
    }

    @GetMapping("/api/comments")
    public List<ChatMessageResponse> getGlobalComments(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
        @RequestParam(required = false) String regionCode
    ) {
        return commentService.getComments(since, regionCode);
    }

    @PostMapping("/api/comments")
    public ChatMessageResponse createGlobalComment(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        AuthenticatedUser user = authService.requireCurrentUser(authorizationHeader);
        return commentService.createComment(request, user);
    }

    @GetMapping("/api/videos/{videoId}/comments")
    public List<ChatMessageResponse> getComments(
        @PathVariable String videoId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
        @RequestParam(required = false) String regionCode
    ) {
        return commentService.getComments(videoId, since, regionCode);
    }

    @GetMapping("/api/videos/{videoId}/comment-highlights")
    public List<CommentHighlightResponse> getCommentHighlights(@PathVariable String videoId) {
        Instant fetchedAt = Instant.now();
        List<SelectedAchievementTitleResponse> titles = achievementTitleService.getPublicTitles();

        return commentHighlightService.getHighlights(videoId).stream()
            .limit(MAX_PUBLIC_COMMENT_HIGHLIGHTS)
            .map(comment -> CommentHighlightResponse.from(videoId, comment, fetchedAt, pickTitle(videoId, comment.id(), titles)))
            .toList();
    }

    private SelectedAchievementTitleResponse pickTitle(
        String videoId,
        String commentId,
        List<SelectedAchievementTitleResponse> titles
    ) {
        if (titles.isEmpty()) {
            return null;
        }

        int index = Math.floorMod((videoId + ":" + commentId).hashCode(), titles.size());
        return titles.get(index);
    }

    @PostMapping("/api/videos/{videoId}/comments")
    public ChatMessageResponse createComment(
        @PathVariable String videoId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        AuthenticatedUser user = authService.requireCurrentUser(authorizationHeader);
        return commentService.createComment(videoId, request, user);
    }
}
