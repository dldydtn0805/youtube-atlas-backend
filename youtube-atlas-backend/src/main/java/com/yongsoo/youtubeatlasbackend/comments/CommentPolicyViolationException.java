package com.yongsoo.youtubeatlasbackend.comments;

public class CommentPolicyViolationException extends RuntimeException {

    private final String code;
    private final Integer retryAfterSeconds;

    public CommentPolicyViolationException(String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
