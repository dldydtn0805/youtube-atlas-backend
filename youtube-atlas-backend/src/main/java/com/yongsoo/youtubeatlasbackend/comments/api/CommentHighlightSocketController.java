package com.yongsoo.youtubeatlasbackend.comments.api;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.yongsoo.youtubeatlasbackend.comments.CommentHighlightStreamService;

@Controller
public class CommentHighlightSocketController {

    private final CommentHighlightStreamService commentHighlightStreamService;

    public CommentHighlightSocketController(CommentHighlightStreamService commentHighlightStreamService) {
        this.commentHighlightStreamService = commentHighlightStreamService;
    }

    @MessageMapping("/comments/highlights/start")
    public void startCommentHighlights(
        CommentHighlightStartRequest request,
        Principal principal,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        commentHighlightStreamService.start(request, principal, headerAccessor.getSessionId());
    }

    @MessageMapping("/comments/highlights/stop")
    public void stopCommentHighlights(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        commentHighlightStreamService.stop(principal, headerAccessor.getSessionId());
    }
}
