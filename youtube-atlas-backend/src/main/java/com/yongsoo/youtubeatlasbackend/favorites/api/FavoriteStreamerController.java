package com.yongsoo.youtubeatlasbackend.favorites.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamerService;

@RestController
@RequestMapping("/api/me/favorite-streamers")
public class FavoriteStreamerController {

    private final FavoriteStreamerService favoriteStreamerService;
    private final AuthService authService;

    public FavoriteStreamerController(FavoriteStreamerService favoriteStreamerService, AuthService authService) {
        this.favoriteStreamerService = favoriteStreamerService;
        this.authService = authService;
    }

    @GetMapping
    public List<FavoriteStreamerResponse> getFavoriteStreamers(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        return favoriteStreamerService.getFavoriteStreamers(authService.requireCurrentUser(authorizationHeader));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteStreamerResponse addFavoriteStreamer(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody CreateFavoriteStreamerRequest request
    ) {
        return favoriteStreamerService.addFavoriteStreamer(authService.requireCurrentUser(authorizationHeader), request);
    }

    @DeleteMapping("/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFavoriteStreamer(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable String channelId
    ) {
        favoriteStreamerService.deleteFavoriteStreamer(authService.requireCurrentUser(authorizationHeader), channelId);
    }
}
