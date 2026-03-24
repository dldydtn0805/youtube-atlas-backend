package com.yongsoo.youtubeatlasbackend.youtube;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
