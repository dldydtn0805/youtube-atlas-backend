package com.yongsoo.youtubeatlasbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AtlasProperties atlasProperties;

    public WebConfig(AtlasProperties atlasProperties) {
        this.atlasProperties = atlasProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(atlasProperties.getRealtime().getAllowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
