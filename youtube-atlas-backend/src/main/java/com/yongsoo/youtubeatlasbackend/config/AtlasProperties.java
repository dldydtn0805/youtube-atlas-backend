package com.yongsoo.youtubeatlasbackend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlas")
public class AtlasProperties {

    private final Youtube youtube = new Youtube();
    private final Realtime realtime = new Realtime();
    private final Trending trending = new Trending();

    public Youtube getYoutube() {
        return youtube;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public Trending getTrending() {
        return trending;
    }

    public static class Youtube {
        private String apiBaseUrl = "https://www.googleapis.com/youtube/v3";
        private String apiKey = "";
        private String categoryLanguage = "ko";
        private int maxResultsPerCategory = 50;
        private int shortsMaxDurationSeconds = 180;

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getCategoryLanguage() {
            return categoryLanguage;
        }

        public void setCategoryLanguage(String categoryLanguage) {
            this.categoryLanguage = categoryLanguage;
        }

        public int getMaxResultsPerCategory() {
            return maxResultsPerCategory;
        }

        public void setMaxResultsPerCategory(int maxResultsPerCategory) {
            this.maxResultsPerCategory = maxResultsPerCategory;
        }

        public int getShortsMaxDurationSeconds() {
            return shortsMaxDurationSeconds;
        }

        public void setShortsMaxDurationSeconds(int shortsMaxDurationSeconds) {
            this.shortsMaxDurationSeconds = shortsMaxDurationSeconds;
        }
    }

    public static class Realtime {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://youtube-atlas.vercel.app",
            "https://*.vercel.app"
        ));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Trending {
        private boolean schedulerEnabled;
        private String cron = "0 0/30 * * * *";
        private List<SyncJob> jobs = new ArrayList<>();

        public boolean isSchedulerEnabled() {
            return schedulerEnabled;
        }

        public void setSchedulerEnabled(boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public List<SyncJob> getJobs() {
            return jobs;
        }

        public void setJobs(List<SyncJob> jobs) {
            this.jobs = jobs;
        }
    }

    public static class SyncJob {
        private String regionCode;
        private String categoryId;
        private String categoryLabel;
        private List<String> sourceCategoryIds = new ArrayList<>();

        public String getRegionCode() {
            return regionCode;
        }

        public void setRegionCode(String regionCode) {
            this.regionCode = regionCode;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public String getCategoryLabel() {
            return categoryLabel;
        }

        public void setCategoryLabel(String categoryLabel) {
            this.categoryLabel = categoryLabel;
        }

        public List<String> getSourceCategoryIds() {
            return sourceCategoryIds;
        }

        public void setSourceCategoryIds(List<String> sourceCategoryIds) {
            this.sourceCategoryIds = sourceCategoryIds;
        }
    }
}
