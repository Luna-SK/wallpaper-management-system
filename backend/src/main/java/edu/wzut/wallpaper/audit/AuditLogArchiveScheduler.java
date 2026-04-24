package edu.wzut.wallpaper.audit;

import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
@EnableScheduling
class AuditLogArchiveScheduler implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(AuditLogArchiveScheduler.class);

	private final AuditLogArchiveService archiveService;

	AuditLogArchiveScheduler(AuditLogArchiveService archiveService) {
		this.archiveService = archiveService;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addTriggerTask(this::runScheduledArchive, currentTrigger());
	}

	private void runScheduledArchive() {
		AuditRetentionSettings settings = archiveService.getSettings();
		if (!settings.archiveEnabled()) {
			log.debug("audit log archive is disabled");
			return;
		}
		archiveService.archiveNow(AuditLogArchiveService.TRIGGER_SCHEDULED);
	}

	private Trigger currentTrigger() {
		return triggerContext -> {
			AuditRetentionSettings settings = archiveService.getSettings();
			return new CronTrigger(settings.archiveCron(), ZoneId.of("Asia/Shanghai"))
					.nextExecution(triggerContext);
		};
	}
}
