package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.game.GameService;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;
    private final AuthService authService;

    public GameController(GameService gameService, AuthService authService) {
        this.gameService = gameService;
        this.authService = authService;
    }

    @GetMapping("/seasons/current")
    public CurrentSeasonResponse getCurrentSeason(@RequestHeader("Authorization") String authorizationHeader) {
        return gameService.getCurrentSeason(authService.requireCurrentUser(authorizationHeader));
    }

    @GetMapping("/wallet")
    public WalletResponse getWallet(@RequestHeader("Authorization") String authorizationHeader) {
        return gameService.getWallet(authService.requireCurrentUser(authorizationHeader));
    }

    @GetMapping("/market")
    public List<MarketVideoResponse> getMarket(@RequestHeader("Authorization") String authorizationHeader) {
        return gameService.getMarket(authService.requireCurrentUser(authorizationHeader));
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> getLeaderboard(@RequestHeader("Authorization") String authorizationHeader) {
        return gameService.getLeaderboard(authService.requireCurrentUser(authorizationHeader));
    }

    @GetMapping("/positions/me")
    public List<PositionResponse> getMyPositions(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam(required = false) String status
    ) {
        return gameService.getMyPositions(authService.requireCurrentUser(authorizationHeader), status);
    }

    @GetMapping("/positions/{positionId}/rank-history")
    public PositionRankHistoryResponse getPositionRankHistory(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long positionId
    ) {
        return gameService.getPositionRankHistory(authService.requireCurrentUser(authorizationHeader), positionId);
    }

    @PostMapping("/positions")
    public PositionResponse buy(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody CreatePositionRequest request
    ) {
        return gameService.buy(authService.requireCurrentUser(authorizationHeader), request);
    }

    @PostMapping("/positions/{positionId}/sell")
    public SellPositionResponse sell(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long positionId
    ) {
        return gameService.sell(authService.requireCurrentUser(authorizationHeader), positionId);
    }
}
