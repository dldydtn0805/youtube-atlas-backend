package com.yongsoo.youtubeatlasbackend.trending.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.trending.TrendingService;

@RestController
@RequestMapping("/api/trending")
public class TrendingController {

    private final TrendingService trendingService;

    public TrendingController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @GetMapping("/signals")
    public List<TrendSignalResponse> getSignals(
        @RequestParam String regionCode,
        @RequestParam String categoryId,
        @RequestParam List<String> videoIds
    ) {
        return trendingService.getSignals(regionCode, categoryId, videoIds);
    }

    @GetMapping("/realtime-surging")
    public RealtimeSurgingResponse getRealtimeSurging(@RequestParam String regionCode) {
        return trendingService.getRealtimeSurging(regionCode);
    }

    @GetMapping("/videos/{videoId}/history")
    public VideoRankHistoryResponse getVideoHistory(
        @RequestParam String regionCode,
        @org.springframework.web.bind.annotation.PathVariable String videoId
    ) {
        return trendingService.getVideoHistory(regionCode, videoId);
    }

    @PostMapping("/sync")
    public SyncTrendingResponse sync(@Valid @RequestBody SyncTrendingRequest request) {
        return trendingService.sync(request);
    }
}
