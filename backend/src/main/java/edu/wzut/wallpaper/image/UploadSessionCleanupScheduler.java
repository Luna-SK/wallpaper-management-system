package edu.wzut.wallpaper.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class UploadSessionCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(UploadSessionCleanupScheduler.class);

	private final ImageService imageService;

	UploadSessionCleanupScheduler(ImageService imageService) {
		this.imageService = imageService;
	}

	@Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
	void cleanupUploadStorage() {
		int expiredSessions = imageService.expireUnconfirmedUploadSessions();
		int orphanObjects = imageService.cleanupOrphanImageObjects();
		if (expiredSessions > 0 || orphanObjects > 0) {
			log.info("cleaned upload storage, expiredSessions={}, orphanObjects={}", expiredSessions, orphanObjects);
		}
	}
}
