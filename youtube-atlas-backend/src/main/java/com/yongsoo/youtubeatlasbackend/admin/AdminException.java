package com.yongsoo.youtubeatlasbackend.admin;

public class AdminException extends RuntimeException {

    private final String code;

    public AdminException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
