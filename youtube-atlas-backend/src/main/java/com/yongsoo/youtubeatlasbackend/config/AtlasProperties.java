package com.yongsoo.youtubeatlasbackend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlas")
public class AtlasProperties {

    private final Youtube youtube = new Youtube();
    private final Realtime realtime = new Realtime();
    private final Trending trending = new Trending();
    private final Game game = new Game();
    private final Auth auth = new Auth();
    private final Admin admin = new Admin();

    public Youtube getYoutube() {
        return youtube;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public Trending getTrending() {
        return trending;
    }

    public Game getGame() {
        return game;
    }

    public Auth getAuth() {
        return auth;
    }

    public Admin getAdmin() {
        return admin;
    }

    public static class Youtube {
        private String apiBaseUrl = "https://www.googleapis.com/youtube/v3";
        private String apiKey = "";
        private String categoryLanguage = "ko";
        private int maxResultsPerCategory = 50;
        private int shortsMaxDurationSeconds = 180;
        private final Cache cache = new Cache();

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

        public Cache getCache() {
            return cache;
        }
    }

    public static class Cache {
        private long categoriesTtlSeconds = 21_600;
        private long videoSectionsTtlSeconds = 120;
        private long categoriesMaxSize = 128;
        private long videoSectionsMaxSize = 512;

        public long getCategoriesTtlSeconds() {
            return categoriesTtlSeconds;
        }

        public void setCategoriesTtlSeconds(long categoriesTtlSeconds) {
            this.categoriesTtlSeconds = categoriesTtlSeconds;
        }

        public long getVideoSectionsTtlSeconds() {
            return videoSectionsTtlSeconds;
        }

        public void setVideoSectionsTtlSeconds(long videoSectionsTtlSeconds) {
            this.videoSectionsTtlSeconds = videoSectionsTtlSeconds;
        }

        public long getCategoriesMaxSize() {
            return categoriesMaxSize;
        }

        public void setCategoriesMaxSize(long categoriesMaxSize) {
            this.categoriesMaxSize = categoriesMaxSize;
        }

        public long getVideoSectionsMaxSize() {
            return videoSectionsMaxSize;
        }

        public void setVideoSectionsMaxSize(long videoSectionsMaxSize) {
            this.videoSectionsMaxSize = videoSectionsMaxSize;
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
        private String cron = "0 0 * * * *";
        private int syncMaxPagesPerSource = 4;
        private int realtimeSurgingRankChangeThreshold = 5;
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

        public int getSyncMaxPagesPerSource() {
            return syncMaxPagesPerSource;
        }

        public void setSyncMaxPagesPerSource(int syncMaxPagesPerSource) {
            this.syncMaxPagesPerSource = syncMaxPagesPerSource;
        }

        public int getRealtimeSurgingRankChangeThreshold() {
            return realtimeSurgingRankChangeThreshold;
        }

        public void setRealtimeSurgingRankChangeThreshold(int realtimeSurgingRankChangeThreshold) {
            this.realtimeSurgingRankChangeThreshold = realtimeSurgingRankChangeThreshold;
        }

        public List<SyncJob> getJobs() {
            return jobs;
        }

        public void setJobs(List<SyncJob> jobs) {
            this.jobs = jobs;
        }
    }

    public static class Auth {
        private String googleClientId = "";
        private String googleClientSecret = "";
        private String googleTokenUrl = "https://oauth2.googleapis.com/token";
        private String googleTokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo";
        private int sessionTtlDays = 30;

        public String getGoogleClientId() {
            return googleClientId;
        }

        public void setGoogleClientId(String googleClientId) {
            this.googleClientId = googleClientId;
        }

        public String getGoogleClientSecret() {
            return googleClientSecret;
        }

        public void setGoogleClientSecret(String googleClientSecret) {
            this.googleClientSecret = googleClientSecret;
        }

        public String getGoogleTokenUrl() {
            return googleTokenUrl;
        }

        public void setGoogleTokenUrl(String googleTokenUrl) {
            this.googleTokenUrl = googleTokenUrl;
        }

        public String getGoogleTokenInfoUrl() {
            return googleTokenInfoUrl;
        }

        public void setGoogleTokenInfoUrl(String googleTokenInfoUrl) {
            this.googleTokenInfoUrl = googleTokenInfoUrl;
        }

        public int getSessionTtlDays() {
            return sessionTtlDays;
        }

        public void setSessionTtlDays(int sessionTtlDays) {
            this.sessionTtlDays = sessionTtlDays;
        }
    }

    public static class Game {
        private boolean schedulerEnabled;
        private String cron = "0 */5 * * * *";

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
    }

    public static class Admin {
        private List<String> allowedEmails = new ArrayList<>();

        public List<String> getAllowedEmails() {
            return allowedEmails;
        }

        public void setAllowedEmails(List<String> allowedEmails) {
            this.allowedEmails = allowedEmails;
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
