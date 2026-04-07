package com.yongsoo.youtubeatlasbackend.admin.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.admin.AdminAccessService;
import com.yongsoo.youtubeatlasbackend.admin.AdminDashboardService;
import com.yongsoo.youtubeatlasbackend.admin.AdminUserService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccessService adminAccessService;
    private final AdminDashboardService adminDashboardService;
    private final AdminUserService adminUserService;

    public AdminController(
        AdminAccessService adminAccessService,
        AdminDashboardService adminDashboardService,
        AdminUserService adminUserService
    ) {
        this.adminAccessService = adminAccessService;
        this.adminDashboardService = adminDashboardService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse getDashboard(@RequestHeader("Authorization") String authorizationHeader) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminDashboardService.getDashboard();
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

    @PatchMapping("/users/{userId}/wallet")
    public AdminUserDetailResponse updateWallet(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable Long userId,
        @RequestBody AdminWalletUpdateRequest request
    ) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminUserService.updateActiveSeasonWallet(userId, request);
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
