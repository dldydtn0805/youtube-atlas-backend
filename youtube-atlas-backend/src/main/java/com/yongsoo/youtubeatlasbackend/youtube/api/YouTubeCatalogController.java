package com.yongsoo.youtubeatlasbackend.youtube.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;

@RestController
@RequestMapping("/api/catalog/regions/{regionCode}")
public class YouTubeCatalogController {

    private final YouTubeCatalogService youTubeCatalogService;

    public YouTubeCatalogController(YouTubeCatalogService youTubeCatalogService) {
        this.youTubeCatalogService = youTubeCatalogService;
    }

    @GetMapping("/categories")
    public List<VideoCategoryResponse> getCategories(@PathVariable String regionCode) {
        return youTubeCatalogService.getCategories(regionCode);
    }

    @GetMapping("/categories/{categoryId}/videos")
    public VideoCategorySectionResponse getPopularVideosByCategory(
        @PathVariable String regionCode,
        @PathVariable String categoryId,
        @RequestParam(required = false) String pageToken
    ) {
        return youTubeCatalogService.getPopularVideosByCategory(regionCode, categoryId, pageToken);
    }
}
