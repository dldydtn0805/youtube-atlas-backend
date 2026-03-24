package com.yongsoo.youtubeatlasbackend.common;

import com.yongsoo.youtubeatlasbackend.comments.CommentPolicyViolationException;
import com.yongsoo.youtubeatlasbackend.comments.CommentValidationException;
import com.yongsoo.youtubeatlasbackend.youtube.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommentPolicyViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleCommentPolicyViolation(CommentPolicyViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), exception.getRetryAfterSeconds()));
    }

    @ExceptionHandler({
        CommentValidationException.class,
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
            .body(new ApiErrorResponse("bad_request", exception.getMessage(), null));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse("not_found", exception.getMessage(), null));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalServiceFailure(ExternalServiceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiErrorResponse("external_service_error", exception.getMessage(), null));
    }
}
