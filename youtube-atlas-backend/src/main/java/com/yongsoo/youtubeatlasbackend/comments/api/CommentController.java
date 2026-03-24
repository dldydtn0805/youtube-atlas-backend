package com.yongsoo.youtubeatlasbackend.comments.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.comments.CommentService;

@RestController
@RequestMapping("/api/videos/{videoId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<ChatMessageResponse> getComments(@PathVariable String videoId) {
        return commentService.getComments(videoId);
    }

    @PostMapping
    public ChatMessageResponse createComment(
        @PathVariable String videoId,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.createComment(videoId, request);
    }
}
