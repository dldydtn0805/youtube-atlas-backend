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
import com.yongsoo.youtubeatlasbackend.comments.CommentPresenceService;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;

@RestController
public class CommentController {

    private final CommentService commentService;
    private final CommentPresenceService commentPresenceService;
    private final AuthService authService;

    public CommentController(
        CommentService commentService,
        CommentPresenceService commentPresenceService,
        AuthService authService
    ) {
        this.commentService = commentService;
        this.commentPresenceService = commentPresenceService;
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
