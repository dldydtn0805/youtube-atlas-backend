package com.yongsoo.youtubeatlasbackend.youtube.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;

@RestController
@RequestMapping("/api/catalog")
public class YouTubeCatalogController {

    private final YouTubeCatalogService youTubeCatalogService;

    public YouTubeCatalogController(YouTubeCatalogService youTubeCatalogService) {
        this.youTubeCatalogService = youTubeCatalogService;
    }

    @GetMapping("/regions/{regionCode}/categories")
    public List<VideoCategoryResponse> getCategories(@PathVariable String regionCode) {
        return youTubeCatalogService.getCategories(regionCode);
    }

    @GetMapping("/regions/{regionCode}/categories/{categoryId}/videos")
    public VideoCategorySectionResponse getPopularVideosByCategory(
        @PathVariable String regionCode,
        @PathVariable String categoryId,
        @RequestParam(required = false) String pageToken
    ) {
        return youTubeCatalogService.getPopularVideosByCategory(regionCode, categoryId, pageToken);
    }

    @GetMapping("/videos/{videoId}")
    public VideoItemResponse getVideoById(@PathVariable String videoId) {
        return youTubeCatalogService.getVideoById(videoId);
    }
}
