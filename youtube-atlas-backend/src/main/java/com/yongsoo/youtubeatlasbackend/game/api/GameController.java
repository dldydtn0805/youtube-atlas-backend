package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.GameScheduledSellOrderService;
import com.yongsoo.youtubeatlasbackend.game.GameService;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;
    private final GameScheduledSellOrderService gameScheduledSellOrderService;
    private final AchievementTitleService achievementTitleService;
    private final AuthService authService;

    public GameController(
        GameService gameService,
        GameScheduledSellOrderService gameScheduledSellOrderService,
        AchievementTitleService achievementTitleService,
        AuthService authService
    ) {
        this.gameService = gameService;
        this.gameScheduledSellOrderService = gameScheduledSellOrderService;
        this.achievementTitleService = achievementTitleService;
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

    @GetMapping("/inventory-slots")
    public InventorySlotResponse getInventorySlots(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getInventorySlots(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/market")
    public List<MarketVideoResponse> getMarket(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getMarket(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/market/buyable-chart")
    public VideoCategorySectionResponse getBuyableMarketChart(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode,
        @RequestParam(required = false) String pageToken
    ) {
        return gameService.getBuyableMarketChart(
            authService.requireCurrentUser(authorizationHeader),
            regionCode,
            pageToken
        );
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> getLeaderboard(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboard(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/highlights")
    public List<GameHighlightResponse> getHighlights(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getHighlights(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/notifications")
    public List<GameNotificationResponse> getNotifications(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getNotifications(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @PatchMapping("/notifications/read")
    public void markNotificationsRead(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        gameService.markNotificationsRead(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @DeleteMapping("/notifications")
    public void deleteNotifications(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        gameService.deleteNotifications(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @DeleteMapping("/notifications/{notificationId}")
    public void deleteNotification(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long notificationId
    ) {
        gameService.deleteNotification(authService.requireCurrentUser(authorizationHeader), notificationId);
    }

    @GetMapping("/tiers/current")
    public TierProgressResponse getCurrentTier(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameService.getCurrentTier(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @GetMapping("/achievement-titles/me")
    public AchievementTitleCollectionResponse getMyAchievementTitles(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        return achievementTitleService.getMyTitles(authService.requireCurrentUser(authorizationHeader));
    }

    @PatchMapping("/achievement-titles/me/selected")
    public AchievementTitleCollectionResponse updateSelectedAchievementTitle(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestBody(required = false) UpdateSelectedAchievementTitleRequest request
    ) {
        return achievementTitleService.updateSelectedTitle(
            authService.requireCurrentUser(authorizationHeader),
            request != null ? request.titleCode() : null
        );
    }

    @GetMapping("/leaderboard/{userId}/positions")
    public List<PositionResponse> getLeaderboardPositions(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboardPositions(authService.requireCurrentUser(authorizationHeader), userId, regionCode);
    }

    @GetMapping("/leaderboard/{userId}/highlights")
    public List<GameHighlightResponse> getLeaderboardHighlights(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboardHighlights(authService.requireCurrentUser(authorizationHeader), userId, regionCode);
    }

    @GetMapping("/leaderboard/{userId}/positions/{positionId}/rank-history")
    public PositionRankHistoryResponse getLeaderboardPositionRankHistory(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @PathVariable Long positionId,
        @RequestParam String regionCode
    ) {
        return gameService.getLeaderboardPositionRankHistory(
            authService.requireCurrentUser(authorizationHeader),
            userId,
            positionId,
            regionCode
        );
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

    @PostMapping("/positions/sell-preview")
    public SellPreviewResponse previewSell(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody SellPositionsRequest request
    ) {
        return gameService.previewSell(authService.requireCurrentUser(authorizationHeader), request);
    }

    @PostMapping("/positions/{positionId}/sell")
    public SellPositionResponse sell(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long positionId
    ) {
        return gameService.sell(authService.requireCurrentUser(authorizationHeader), positionId);
    }

    @PostMapping("/scheduled-sell-orders")
    public ScheduledSellOrderResponse createScheduledSellOrder(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody CreateScheduledSellOrderRequest request
    ) {
        return gameScheduledSellOrderService.create(authService.requireCurrentUser(authorizationHeader), request);
    }

    @GetMapping("/scheduled-sell-orders")
    public List<ScheduledSellOrderResponse> getScheduledSellOrders(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam String regionCode
    ) {
        return gameScheduledSellOrderService.list(authService.requireCurrentUser(authorizationHeader), regionCode);
    }

    @DeleteMapping("/scheduled-sell-orders/{orderId}")
    public ScheduledSellOrderResponse cancelScheduledSellOrder(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long orderId
    ) {
        return gameScheduledSellOrderService.cancel(authService.requireCurrentUser(authorizationHeader), orderId);
    }
}
