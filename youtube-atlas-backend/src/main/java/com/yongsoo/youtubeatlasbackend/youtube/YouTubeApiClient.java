package com.yongsoo.youtubeatlasbackend.youtube;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yongsoo.youtubeatlasbackend.common.ExternalServiceException;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasContentDetails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasCommentHighlight;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnail;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

@Component
public class YouTubeApiClient {

    private final AtlasProperties atlasProperties;
    private final RestTemplate restTemplate;

    public YouTubeApiClient(AtlasProperties atlasProperties, RestTemplateBuilder restTemplateBuilder) {
        this.atlasProperties = atlasProperties;
        this.restTemplate = restTemplateBuilder.build();
    }

    public List<RemoteVideoCategoryItem> fetchVideoCategories(String regionCode) {
        String apiKey = requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(atlasProperties.getYoutube().getApiBaseUrl())
            .path("/videoCategories")
            .queryParam("part", "snippet")
            .queryParam("regionCode", regionCode)
            .queryParam("hl", atlasProperties.getYoutube().getCategoryLanguage())
            .queryParam("key", apiKey)
            .toUriString();

        RemoteVideoCategoryListResponse result = get(url, RemoteVideoCategoryListResponse.class);
        return result.items() != null ? result.items() : List.of();
    }

    public RemoteVideoPage fetchMostPopularVideos(String regionCode, String categoryId, String pageToken) {
        String apiKey = requireApiKey();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(atlasProperties.getYoutube().getApiBaseUrl())
            .path("/videos")
            .queryParam("part", "snippet,contentDetails,statistics")
            .queryParam("chart", "mostPopular")
            .queryParam("regionCode", regionCode)
            .queryParam("maxResults", atlasProperties.getYoutube().getMaxResultsPerCategory())
            .queryParam("key", apiKey);

        if (StringUtils.hasText(categoryId)) {
            builder.queryParam("videoCategoryId", categoryId);
        }

        if (StringUtils.hasText(pageToken)) {
            builder.queryParam("pageToken", pageToken);
        }

        RemoteVideoListResponse result = get(builder.toUriString(), RemoteVideoListResponse.class);
        List<AtlasVideo> items = new ArrayList<>();

        if (result.items() != null) {
            for (RemoteVideoItem item : result.items()) {
                items.add(toVideo(item));
            }
        }

        return new RemoteVideoPage(items, result.nextPageToken());
    }

    public List<AtlasVideo> fetchVideosByIds(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return List.of();
        }

        List<String> normalizedVideoIds = videoIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        if (normalizedVideoIds.isEmpty()) {
            return List.of();
        }

        String apiKey = requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(atlasProperties.getYoutube().getApiBaseUrl())
            .path("/videos")
            .queryParam("part", "snippet,contentDetails,statistics")
            .queryParam("id", String.join(",", normalizedVideoIds))
            .queryParam("key", apiKey)
            .toUriString();

        RemoteVideoListResponse result = get(url, RemoteVideoListResponse.class);

        if (result.items() == null) {
            return List.of();
        }

        return result.items().stream()
            .map(this::toVideo)
            .toList();
    }

    public List<AtlasCommentHighlight> fetchTopLevelCommentHighlights(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            return List.of();
        }

        String apiKey = requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(atlasProperties.getYoutube().getApiBaseUrl())
            .path("/commentThreads")
            .queryParam("part", "snippet")
            .queryParam("videoId", videoId.trim())
            .queryParam("maxResults", 100)
            .queryParam("order", "relevance")
            .queryParam("textFormat", "plainText")
            .queryParam("key", apiKey)
            .toUriString();

        RemoteCommentThreadListResponse result = get(url, RemoteCommentThreadListResponse.class);
        if (result.items() == null) {
            return List.of();
        }

        return result.items().stream()
            .map(this::toCommentHighlight)
            .filter(comment -> StringUtils.hasText(comment.id()) && StringUtils.hasText(comment.text()))
            .toList();
    }

    public boolean isIgnorableCategoryFetchError(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (
            message.contains("The requested video chart is not supported or is not available.")
                || message.contains("Requested entity was not found.")
        );
    }

    private <T extends RemoteApiResponse> T get(String url, Class<T> type) {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(url, type);
            T body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new ExternalServiceException("YouTube API 요청에 실패했습니다.");
            }

            if (body.error() != null && StringUtils.hasText(body.error().message())) {
                throw new ExternalServiceException(body.error().message());
            }

            return body;
        } catch (RestClientException exception) {
            throw new ExternalServiceException("YouTube API 요청에 실패했습니다.", exception);
        }
    }

    private String requireApiKey() {
        String apiKey = atlasProperties.getYoutube().getApiKey();

        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("YOUTUBE_API_KEY 환경 변수가 설정되지 않았습니다.");
        }

        return apiKey;
    }

    private AtlasVideo toVideo(RemoteVideoItem item) {
        RemoteSnippet snippet = item.snippet() != null ? item.snippet() : new RemoteSnippet(null, null, null, null, null, null);
        RemoteContentDetails contentDetails = item.contentDetails() != null ? item.contentDetails() : new RemoteContentDetails(null);
        RemoteStatistics statistics = item.statistics() != null ? item.statistics() : new RemoteStatistics(null);

        return new AtlasVideo(
            item.id(),
            new AtlasContentDetails(contentDetails.duration()),
            new AtlasVideoSnippet(
                snippet.title(),
                snippet.channelTitle(),
                snippet.channelId(),
                snippet.categoryId(),
                parsePublishedAt(snippet.publishedAt()),
                toThumbnails(snippet.thumbnails())
            ),
            new AtlasVideoStatistics(parseLong(statistics.viewCount()))
        );
    }

    private AtlasCommentHighlight toCommentHighlight(RemoteCommentThreadItem item) {
        RemoteTopLevelComment topLevelComment = item.snippet() != null ? item.snippet().topLevelComment() : null;
        RemoteCommentSnippet snippet = topLevelComment != null ? topLevelComment.snippet() : null;

        if (snippet == null) {
            return new AtlasCommentHighlight(item.id(), null, null, null, null);
        }

        return new AtlasCommentHighlight(
            StringUtils.hasText(topLevelComment.id()) ? topLevelComment.id() : item.id(),
            snippet.authorDisplayName(),
            StringUtils.hasText(snippet.textDisplay()) ? snippet.textDisplay() : snippet.textOriginal(),
            snippet.likeCount(),
            parseOffsetDateTime(snippet.publishedAt())
        );
    }

    private OffsetDateTime parsePublishedAt(String publishedAt) {
        return parseOffsetDateTime(publishedAt);
    }

    private OffsetDateTime parseOffsetDateTime(String publishedAt) {
        if (!StringUtils.hasText(publishedAt)) {
            return null;
        }

        return OffsetDateTime.parse(publishedAt);
    }

    private AtlasThumbnails toThumbnails(RemoteThumbnails thumbnails) {
        if (thumbnails == null) {
            return new AtlasThumbnails(null, null, null, null, null);
        }

        return new AtlasThumbnails(
            toThumbnail(thumbnails.defaultThumbnail()),
            toThumbnail(thumbnails.medium()),
            toThumbnail(thumbnails.high()),
            toThumbnail(thumbnails.standard()),
            toThumbnail(thumbnails.maxres())
        );
    }

    private AtlasThumbnail toThumbnail(RemoteThumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }

        return new AtlasThumbnail(thumbnail.url(), thumbnail.width(), thumbnail.height());
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record RemoteVideoPage(
        List<AtlasVideo> items,
        String nextPageToken
    ) {
    }

    public sealed interface RemoteApiResponse permits
        RemoteVideoCategoryListResponse,
        RemoteVideoListResponse,
        RemoteCommentThreadListResponse {
        RemoteApiError error();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteApiError(
        String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteVideoCategoryListResponse(
        List<RemoteVideoCategoryItem> items,
        RemoteApiError error
    ) implements RemoteApiResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteVideoCategoryItem(
        String id,
        RemoteCategorySnippet snippet
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCategorySnippet(
        boolean assignable,
        String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteVideoListResponse(
        List<RemoteVideoItem> items,
        String nextPageToken,
        RemoteApiError error
    ) implements RemoteApiResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCommentThreadListResponse(
        List<RemoteCommentThreadItem> items,
        RemoteApiError error
    ) implements RemoteApiResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCommentThreadItem(
        String id,
        RemoteCommentThreadSnippet snippet
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCommentThreadSnippet(
        RemoteTopLevelComment topLevelComment
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteTopLevelComment(
        String id,
        RemoteCommentSnippet snippet
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCommentSnippet(
        String authorDisplayName,
        String textDisplay,
        String textOriginal,
        Long likeCount,
        String publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteVideoItem(
        String id,
        RemoteContentDetails contentDetails,
        RemoteSnippet snippet,
        RemoteStatistics statistics
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteContentDetails(
        String duration
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteSnippet(
        String title,
        String channelTitle,
        String channelId,
        String categoryId,
        String publishedAt,
        RemoteThumbnails thumbnails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteStatistics(
        String viewCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteThumbnails(
        @JsonProperty("default") RemoteThumbnail defaultThumbnail,
        RemoteThumbnail medium,
        RemoteThumbnail high,
        RemoteThumbnail standard,
        RemoteThumbnail maxres
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteThumbnail(
        String url,
        Integer width,
        Integer height
    ) {
    }
}
