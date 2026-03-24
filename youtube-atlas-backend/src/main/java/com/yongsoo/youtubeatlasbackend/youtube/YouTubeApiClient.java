package com.yongsoo.youtubeatlasbackend.youtube;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnail;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

@Component
public class YouTubeApiClient {

    private static final Pattern ISO_DURATION_PATTERN = Pattern.compile(
        "^P(?:\\d+Y)?(?:\\d+M)?(?:\\d+W)?(?:\\d+D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$"
    );
    private static final Pattern SHORTS_TITLE_PATTERN = Pattern.compile("#shorts\\b|\\bshorts?\\b|쇼츠", Pattern.CASE_INSENSITIVE);

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
                AtlasVideo video = toVideo(item);

                if (!isShortFormVideo(video)) {
                    items.add(video);
                }
            }
        }

        return new RemoteVideoPage(items, result.nextPageToken());
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
        RemoteSnippet snippet = item.snippet() != null ? item.snippet() : new RemoteSnippet(null, null, null, null, null);
        RemoteContentDetails contentDetails = item.contentDetails() != null ? item.contentDetails() : new RemoteContentDetails(null);
        RemoteStatistics statistics = item.statistics() != null ? item.statistics() : new RemoteStatistics(null);

        return new AtlasVideo(
            item.id(),
            new AtlasContentDetails(contentDetails.duration()),
            new AtlasVideoSnippet(
                snippet.title(),
                snippet.channelTitle(),
                snippet.categoryId(),
                parsePublishedAt(snippet.publishedAt()),
                toThumbnails(snippet.thumbnails())
            ),
            new AtlasVideoStatistics(parseLong(statistics.viewCount()))
        );
    }

    private OffsetDateTime parsePublishedAt(String publishedAt) {
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

    private boolean isShortFormVideo(AtlasVideo video) {
        String duration = video.contentDetails() != null ? video.contentDetails().duration() : null;
        if (parseIso8601DurationToSeconds(duration) <= atlasProperties.getYoutube().getShortsMaxDurationSeconds()) {
            return true;
        }

        String title = video.snippet() != null ? video.snippet().title() : "";
        return title != null && SHORTS_TITLE_PATTERN.matcher(title).find();
    }

    private long parseIso8601DurationToSeconds(String duration) {
        if (!StringUtils.hasText(duration)) {
            return Long.MAX_VALUE;
        }

        Matcher matcher = ISO_DURATION_PATTERN.matcher(duration);
        if (!matcher.matches()) {
            return Long.MAX_VALUE;
        }

        long hours = parsePart(matcher.group(1));
        long minutes = parsePart(matcher.group(2));
        long seconds = parsePart(matcher.group(3));
        return hours * 3600 + minutes * 60 + seconds;
    }

    private long parsePart(String value) {
        return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
    }

    public record RemoteVideoPage(
        List<AtlasVideo> items,
        String nextPageToken
    ) {
    }

    public sealed interface RemoteApiResponse permits RemoteVideoCategoryListResponse, RemoteVideoListResponse {
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
