package com.luna.wallpaper.image;

import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import com.luna.wallpaper.settings.SoftDeleteCleanupSettings;
import com.luna.wallpaper.settings.SystemSettingService;

@Component
class SoftDeleteCleanupScheduler implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupScheduler.class);

	private final ImageService imageService;
	private final SystemSettingService settings;

	SoftDeleteCleanupScheduler(ImageService imageService, SystemSettingService settings) {
		this.imageService = imageService;
		this.settings = settings;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addTriggerTask(this::cleanupExpiredDeletedImages, currentTrigger());
	}

	@EventListener(ApplicationReadyEvent.class)
	void cleanupAfterStartup() {
		cleanupExpiredDeletedImages();
	}

	void cleanupExpiredDeletedImages() {
		int deletedImages = imageService.purgeExpiredDeletedImages();
		if (deletedImages > 0) {
			log.info("cleaned expired soft-deleted images, deletedImages={}", deletedImages);
		}
	}

	private Trigger currentTrigger() {
		return triggerContext -> {
			String cron = settings.get(SoftDeleteCleanupSettings.CLEANUP_CRON,
					SoftDeleteCleanupSettings.DEFAULT_CLEANUP_CRON);
			if (!CronExpression.isValidExpression(cron)) {
				cron = SoftDeleteCleanupSettings.DEFAULT_CLEANUP_CRON;
			}
			return new CronTrigger(cron, ZoneId.of("Asia/Shanghai")).nextExecution(triggerContext);
		};
	}
}
