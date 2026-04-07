package com.yongsoo.youtubeatlasbackend.admin.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.admin.AdminAccessService;
import com.yongsoo.youtubeatlasbackend.admin.AdminDashboardService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccessService adminAccessService;
    private final AdminDashboardService adminDashboardService;

    public AdminController(AdminAccessService adminAccessService, AdminDashboardService adminDashboardService) {
        this.adminAccessService = adminAccessService;
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse getDashboard(@RequestHeader("Authorization") String authorizationHeader) {
        adminAccessService.requireAdmin(authorizationHeader);
        return adminDashboardService.getDashboard();
    }
}
