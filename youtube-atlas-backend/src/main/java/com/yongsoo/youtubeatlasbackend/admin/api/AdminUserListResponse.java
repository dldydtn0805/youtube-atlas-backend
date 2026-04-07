package com.yongsoo.youtubeatlasbackend.admin.api;

import java.util.List;

public record AdminUserListResponse(
    String query,
    int limit,
    int count,
    List<AdminUserSummaryResponse> users
) {
}
