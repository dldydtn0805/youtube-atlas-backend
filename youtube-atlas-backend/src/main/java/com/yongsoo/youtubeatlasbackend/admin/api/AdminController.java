package com.yongsoo.youtubeatlasbackend.admin.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.admin.AdminAccessService;
import com.yongsoo.youtubeatlasbackend.admin.AdminCommentService;
import com.yongsoo.youtubeatlasbackend.admin.AdminDashboardService;
import com.yongsoo.youtubeatlasbackend.admin.AdminPositionService;
import com.yongsoo.youtubeatlasbackend.admin.AdminSeasonService;
import com.yongsoo.youtubeatlasbackend.admin.AdminTradeHistoryService;
import com.yongsoo.youtubeatlasbackend.admin.AdminTrendSnapshotHistoryService;
import com.yongsoo.youtubeatlasbackend.admin.AdminUserService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccessService adminAccessService;
    private final AdminCommentService adminCommentService;
    private final AdminDashboardService adminDashboardService;
    private final AdminPositionService adminPositionService;
    private final AdminUserService adminUserService;
    private final AdminSeasonService adminSeasonService;
    private final AdminTradeHistoryService adminTradeHistoryService;
    private final AdminTrendSnapshotHistoryService adminTrendSnapshotHistoryService;

    public AdminController(
        AdminAccessService adminAccessService,
        AdminCommentService adminCommentService,
        AdminDashboardService adminDashboardService,
        AdminPositionService adminPositionService,
        AdminUserService adminUserService,
        AdminSeasonService adminSeasonService,
        AdminTradeHistoryService adminTradeHistoryService,
        AdminTrendSnapshotHistoryService adminTrendSnapshotHistoryService
    ) {
        this.adminAccessService = adminAccessService;
        this.adminCommentService = adminCommentService;
        this.adminDashboardService = adminDashboardService;
        this.adminPositionService = adminPositionService;
        this.adminUserService = adminUserService;
        this.adminSeasonService = adminSeasonService;
        this.adminTradeHistoryService = adminTradeHistoryService;
        this.adminTrendSnapshotHistoryService = adminTrendSnapshotHistoryService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse getDashboard(@RequestHeader("Authorization") String authorizationHeader) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminDashboardService.getDashboard();
    }

    @GetMapping("/trend-snapshots")
    public AdminTrendSnapshotHistoryResponse getTrendSnapshots(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam java.time.Instant startAt,
        @RequestParam java.time.Instant endAt,
        @RequestParam(required = false) String regionCode
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminTrendSnapshotHistoryService.getSnapshotsBySavedAtRange(startAt, endAt, regionCode);
    }

    @PostMapping("/comments/purge")
    public AdminCommentCleanupResponse purgeOldComments(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody AdminCommentCleanupRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminCommentService.deleteCommentsOlderThan(request);
    }

    @PostMapping("/trade-history/purge")
    public AdminTradeHistoryCleanupResponse purgeOldTradeHistory(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody AdminTradeHistoryCleanupRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminTradeHistoryService.deleteClosedTradeHistoryOlderThan(request);
    }

    @PatchMapping("/seasons/{seasonId}")
    public AdminSeasonSummaryResponse updateSeasonSchedule(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long seasonId,
        @RequestBody AdminSeasonScheduleUpdateRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminSeasonService.updateSeasonSchedule(seasonId, request);
    }

    @PatchMapping("/seasons/{seasonId}/starting-balance")
    public AdminSeasonSummaryResponse updateSeasonStartingBalance(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long seasonId,
        @Valid @RequestBody AdminSeasonStartingBalanceUpdateRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminSeasonService.updateStartingBalance(seasonId, request);
    }

    @PostMapping("/seasons/{seasonId}/close")
    public void closeSeason(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long seasonId
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        adminSeasonService.closeSeason(seasonId);
    }

    @GetMapping("/users")
    public AdminUserListResponse getUsers(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam(required = false, name = "q") String query,
        @RequestParam(required = false) Integer limit
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminUserService.getUsers(query, limit);
    }

    @GetMapping("/users/{userId}")
    public AdminUserDetailResponse getUser(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminUserService.getUser(userId);
    }

    @GetMapping("/users/{userId}/positions")
    public java.util.List<AdminUserPositionResponse> getUserPositions(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestParam Long seasonId
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminPositionService.getOpenPositions(userId, seasonId);
    }

    @PatchMapping("/users/{userId}/wallet")
    public AdminUserDetailResponse updateWallet(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestBody AdminWalletUpdateRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminUserService.updateActiveSeasonWallet(userId, request);
    }

    @PatchMapping("/users/{userId}/positions/{positionId}")
    public AdminUserPositionResponse updateUserPosition(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @PathVariable Long positionId,
        @Valid @RequestBody AdminPositionUpdateRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminPositionService.updateOpenPosition(userId, positionId, request);
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        adminUserService.deleteUser(userId);
    }
}
