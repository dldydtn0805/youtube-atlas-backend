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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;

@RestController
@RequestMapping("/api/videos/{videoId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final AuthService authService;

    public CommentController(CommentService commentService, AuthService authService) {
        this.commentService = commentService;
        this.authService = authService;
    }

    @GetMapping
    public List<ChatMessageResponse> getComments(
        @PathVariable String videoId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        return commentService.getComments(videoId, since);
    }

    @PostMapping
    public ChatMessageResponse createComment(
        @PathVariable String videoId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.createComment(videoId, request, authService.getCurrentUserOrNull(authorizationHeader));
    }
}
