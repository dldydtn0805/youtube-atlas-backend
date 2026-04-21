package com.yongsoo.youtubeatlasbackend.trending.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.trending.TrendingService;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;

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

    @GetMapping("/top-rank-risers")
    public TopRankRisersResponse getTopRankRisers(@RequestParam String regionCode) {
        return trendingService.getTopRankRisers(regionCode);
    }

    @GetMapping("/new-entries")
    public NewChartEntriesResponse getNewChartEntries(@RequestParam String regionCode) {
        return trendingService.getNewChartEntries(regionCode);
    }

    @GetMapping("/top-videos")
    public VideoCategorySectionResponse getTopVideos(
        @RequestParam String regionCode,
        @RequestParam(required = false) String pageToken
    ) {
        return trendingService.getTopVideos(regionCode, pageToken);
    }

    @GetMapping("/music-top-videos")
    public VideoCategorySectionResponse getMusicTopVideos(
        @RequestParam String regionCode,
        @RequestParam(required = false) String pageToken
    ) {
        return trendingService.getMusicTopVideos(regionCode, pageToken);
    }

    @GetMapping("/videos/{videoId}/history")
    public VideoRankHistoryResponse getVideoHistory(
        @RequestParam String regionCode,
        @org.springframework.web.bind.annotation.PathVariable String videoId
    ) {
        return trendingService.getVideoHistory(regionCode, videoId);
    }
}
