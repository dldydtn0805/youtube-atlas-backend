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
    public CurrentSeasonResponse getCurrentSeason(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getCurrentSeason(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/wallet")
    public WalletResponse getWallet(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getWallet(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/market")
    public List<MarketVideoResponse> getMarket(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getMarket(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> getLeaderboard(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboard(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/dividends/overview")
    public DividendOverviewResponse getDividendOverview(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getDividendOverview(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/leaderboard/{userId}/positions")
    public List<PositionResponse> getLeaderboardPositions(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboardPositions(authService.requireCurrentUser(authorizationHeader), userId, regionCode);
    }

    @GetMapping("/positions/me")
    public List<PositionResponse> getMyPositions(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit
    ) {
        return gameService.getMyPositions(authService.requireCurrentUser(authorizationHeader), regionCode, status, limit);
    }

    @GetMapping("/positions/{positionId}/rank-history")
    public PositionRankHistoryResponse getPositionRankHistory(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long positionId
    ) {
        return gameService.getPositionRankHistory(authService.requireCurrentUser(authorizationHeader), positionId);
    }

    @PostMapping("/positions")
    public List<PositionResponse> buy(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody CreatePositionRequest request
    ) {
        return gameService.buy(authService.requireCurrentUser(authorizationHeader), request);
    }

    @PostMapping("/positions/sell")
    public List<SellPositionResponse> sell(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody SellPositionsRequest request
    ) {
        return gameService.sell(authService.requireCurrentUser(authorizationHeader), request);
    }

    @PostMapping("/positions/{positionId}/sell")
    public SellPositionResponse sell(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long positionId
    ) {
        return gameService.sell(authService.requireCurrentUser(authorizationHeader), positionId);
    }
}
