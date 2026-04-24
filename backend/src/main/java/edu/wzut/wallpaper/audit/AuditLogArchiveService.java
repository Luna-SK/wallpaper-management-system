package edu.wzut.wallpaper.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.wzut.wallpaper.config.StorageProperties;
import edu.wzut.wallpaper.settings.SystemSettingService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditLogArchiveService {

	static final String TRIGGER_SCHEDULED = "SCHEDULED";
	static final String TRIGGER_MANUAL = "MANUAL";

	private static final String RETENTION_DAYS = "audit.retention.days";
	private static final String ARCHIVE_ENABLED = "audit.archive.enabled";
	private static final String ARCHIVE_CRON = "audit.archive.cron";
	private static final String ARCHIVE_STORAGE = "audit.archive.storage";
	private static final String ARCHIVE_BATCH_SIZE = "audit.archive.batch_size";

	private static final AuditRetentionSettings DEFAULT_SETTINGS =
			new AuditRetentionSettings(180, true, "0 30 2 * * *", "RUSTFS", 1000);
	private static final DateTimeFormatter OBJECT_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final AuditLogRepository auditLogRepository;
	private final AuditLogArchiveRunRepository archiveRunRepository;
	private final SystemSettingService systemSettingService;
	private final StorageProperties storageProperties;
	private final S3Client s3Client;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	AuditLogArchiveService(AuditLogRepository auditLogRepository,
			AuditLogArchiveRunRepository archiveRunRepository,
			SystemSettingService systemSettingService,
			StorageProperties storageProperties,
			S3Client s3Client,
			ObjectMapper objectMapper) {
		this(auditLogRepository, archiveRunRepository, systemSettingService,
				storageProperties, s3Client, objectMapper, Clock.systemDefaultZone());
	}

	AuditLogArchiveService(AuditLogRepository auditLogRepository,
			AuditLogArchiveRunRepository archiveRunRepository,
			SystemSettingService systemSettingService,
			StorageProperties storageProperties,
			S3Client s3Client,
			ObjectMapper objectMapper,
			Clock clock) {
		this.auditLogRepository = auditLogRepository;
		this.archiveRunRepository = archiveRunRepository;
		this.systemSettingService = systemSettingService;
		this.storageProperties = storageProperties;
		this.s3Client = s3Client;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AuditRetentionSettings getSettings() {
		return new AuditRetentionSettings(
				readInt(RETENTION_DAYS, DEFAULT_SETTINGS.retentionDays()),
				readBoolean(ARCHIVE_ENABLED, DEFAULT_SETTINGS.archiveEnabled()),
				systemSettingService.get(ARCHIVE_CRON, DEFAULT_SETTINGS.archiveCron()),
				systemSettingService.get(ARCHIVE_STORAGE, DEFAULT_SETTINGS.archiveStorage()),
				readInt(ARCHIVE_BATCH_SIZE, DEFAULT_SETTINGS.batchSize()));
	}

	@Transactional
	public AuditRetentionSettings updateSettings(AuditRetentionUpdateRequest request) {
		if (!CronExpression.isValidExpression(request.archiveCron())) {
			throw new IllegalArgumentException("archiveCron must be a valid Spring cron expression");
		}
		if (!"RUSTFS".equalsIgnoreCase(request.archiveStorage())) {
			throw new IllegalArgumentException("archiveStorage currently only supports RUSTFS");
		}
		systemSettingService.put(RETENTION_DAYS, String.valueOf(request.retentionDays()));
		systemSettingService.put(ARCHIVE_ENABLED, String.valueOf(request.archiveEnabled()));
		systemSettingService.put(ARCHIVE_CRON, request.archiveCron());
		systemSettingService.put(ARCHIVE_STORAGE, request.archiveStorage().toUpperCase());
		systemSettingService.put(ARCHIVE_BATCH_SIZE, String.valueOf(request.batchSize()));
		return getSettings();
	}

	public synchronized AuditArchiveRunResponse archiveNow(String triggerType) {
		AuditRetentionSettings settings = getSettings();
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime cutoffTime = now.minusDays(settings.retentionDays());
		AuditLogArchiveRun run = archiveRunRepository.save(
				new AuditLogArchiveRun(UUID.randomUUID().toString(), triggerType, cutoffTime));

		try {
			List<AuditLog> logs = findLogsToArchive(cutoffTime, settings.batchSize());
			String bucket = storageProperties.bucketAudit();
			String objectKey = buildObjectKey(now);
			if (!logs.isEmpty()) {
				byte[] payload = buildArchivePayload(logs);
				putArchiveObject(bucket, objectKey, payload);
			}
			long deletedCount = deleteArchivedLogs(logs, settings.batchSize());
			run.succeed(bucket, logs.isEmpty() ? null : objectKey, logs.size(), deletedCount);
		}
		catch (Exception exception) {
			run.fail(exception);
		}

		return AuditArchiveRunResponse.from(archiveRunRepository.save(run));
	}

	@Transactional(readOnly = true)
	public List<AuditArchiveRunResponse> listArchiveRuns(int limit) {
		return archiveRunRepository.findAllByOrderByStartedAtDesc(
						PageRequest.of(0, Math.clamp(limit, 1, 100)))
				.stream()
				.map(AuditArchiveRunResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public long countExpiredLogs() {
		AuditRetentionSettings settings = getSettings();
		LocalDateTime cutoffTime = LocalDateTime.now(clock).minusDays(settings.retentionDays());
		return auditLogRepository.countByCreatedAtBefore(cutoffTime);
	}

	private List<AuditLog> findLogsToArchive(LocalDateTime cutoffTime, int batchSize) {
		List<AuditLog> logs = new ArrayList<>();
		int page = 0;
		while (true) {
			List<AuditLog> batch = auditLogRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(
					cutoffTime, PageRequest.of(page, batchSize));
			if (batch.isEmpty()) {
				return logs;
			}
			logs.addAll(batch);
			if (batch.size() < batchSize) {
				return logs;
			}
			if (logs.size() >= batchSize * 10) {
				return logs;
			}
			page++;
		}
	}

	private long deleteArchivedLogs(List<AuditLog> logs, int batchSize) {
		long deletedCount = 0;
		for (int start = 0; start < logs.size(); start += batchSize) {
			int end = Math.min(start + batchSize, logs.size());
			List<String> ids = logs.subList(start, end).stream()
					.map(AuditLog::getId)
					.toList();
			auditLogRepository.deleteAllByIdInBatch(ids);
			deletedCount += ids.size();
		}
		return deletedCount;
	}

	private byte[] buildArchivePayload(List<AuditLog> logs) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
			for (AuditLog log : logs) {
				gzip.write(objectMapper.writeValueAsString(toArchiveLine(log)).getBytes(StandardCharsets.UTF_8));
				gzip.write('\n');
			}
		}
		return output.toByteArray();
	}

	private Map<String, Object> toArchiveLine(AuditLog log) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", log.getId());
		line.put("actorId", log.getActorId());
		line.put("action", log.getAction());
		line.put("targetType", log.getTargetType());
		line.put("targetId", log.getTargetId());
		line.put("ipAddress", log.getIpAddress());
		line.put("userAgent", log.getUserAgent());
		line.put("detail", parseDetail(log.getDetailJson()));
		line.put("createdAt", log.getCreatedAt());
		return line;
	}

	private Object parseDetail(String detailJson) {
		if (detailJson == null || detailJson.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(detailJson);
		}
		catch (JacksonException exception) {
			return detailJson;
		}
	}

	private void putArchiveObject(String bucket, String objectKey, byte[] payload) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.contentType("application/gzip")
				.build();
		s3Client.putObject(request, RequestBody.fromBytes(payload));
	}

	private String buildObjectKey(LocalDateTime now) {
		return "audit-logs/%d/%02d/audit-log-archive-%s.jsonl.gz".formatted(
				now.getYear(), now.getMonthValue(), now.format(OBJECT_TIME_FORMAT));
	}

	private int readInt(String key, int defaultValue) {
		try {
			return Integer.parseInt(systemSettingService.get(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private boolean readBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(systemSettingService.get(key, String.valueOf(defaultValue)));
	}
}
