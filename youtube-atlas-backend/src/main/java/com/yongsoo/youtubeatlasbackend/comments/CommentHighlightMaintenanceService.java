package com.yongsoo.youtubeatlasbackend.comments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CommentHighlightMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(CommentHighlightMaintenanceService.class);

    private final CommentHighlightService commentHighlightService;

    public CommentHighlightMaintenanceService(CommentHighlightService commentHighlightService) {
        this.commentHighlightService = commentHighlightService;
    }

    @Scheduled(fixedDelayString = "${COMMENT_HIGHLIGHT_CLEANUP_FIXED_DELAY_MS:3600000}")
    public void purgeExpiredHighlights() {
        try {
            long deletedHighlights = commentHighlightService.purgeExpiredHighlights();
            if (deletedHighlights > 0) {
                log.info("Purged {} expired comment highlights", deletedHighlights);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to purge expired comment highlights", exception);
        }
    }
}
