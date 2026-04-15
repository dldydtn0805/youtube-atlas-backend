package com.yongsoo.youtubeatlasbackend.common;

import com.yongsoo.youtubeatlasbackend.auth.AuthException;
import com.yongsoo.youtubeatlasbackend.admin.AdminException;
import com.yongsoo.youtubeatlasbackend.comments.CommentPolicyViolationException;
import com.yongsoo.youtubeatlasbackend.comments.CommentValidationException;
import com.yongsoo.youtubeatlasbackend.youtube.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        log.error("External service failure: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiErrorResponse("external_service_error", exception.getMessage(), null));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthFailure(AuthException exception) {
        log.warn("Authentication failure: code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<ApiErrorResponse> handleAdminFailure(AdminException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), null));
    }
}
