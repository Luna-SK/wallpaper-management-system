package com.luna.wallpaper.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.image.ImageDtos.ImageBatchRequest;
import com.luna.wallpaper.image.ImageDtos.ImagePageResponse;
import com.luna.wallpaper.image.ImageDtos.ImageResponse;
import com.luna.wallpaper.image.ImageDtos.ImageUpdateRequest;
import com.luna.wallpaper.image.ImageDtos.UploadBatchResponse;
import com.luna.wallpaper.image.ImageDtos.UploadSessionCreateRequest;
import com.luna.wallpaper.settings.SoftDeleteCleanupSettings;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.settings.UploadLimitService;
import com.luna.wallpaper.settings.UploadLimitService.UploadLimitSettings;
import com.luna.wallpaper.taxonomy.Category;
import com.luna.wallpaper.taxonomy.CategoryMapper;
import com.luna.wallpaper.taxonomy.Tag;
import com.luna.wallpaper.taxonomy.TagGroup;
import com.luna.wallpaper.taxonomy.TagGroupMapper;
import com.luna.wallpaper.taxonomy.TagMapper;

@Service
class ImageService {

	private final ImageAssetMapper images;
	private final ImageVersionMapper versions;
	private final UploadBatchMapper batches;
	private final UploadBatchItemMapper batchItems;
	private final ImageTagMapper imageTags;
	private final UploadBatchTagMapper uploadBatchTags;
	private final CategoryMapper categories;
	private final TagGroupMapper tagGroups;
	private final TagMapper tags;
	private final ImageStorageService storage;
	private final SystemSettingService settings;
	private final UploadLimitService uploadLimitService;
	private final AuditLogService auditLogService;

	ImageService(ImageAssetMapper images, ImageVersionMapper versions, UploadBatchMapper batches,
			UploadBatchItemMapper batchItems, ImageTagMapper imageTags, UploadBatchTagMapper uploadBatchTags,
			CategoryMapper categories, TagGroupMapper tagGroups, TagMapper tags,
			ImageStorageService storage, SystemSettingService settings, UploadLimitService uploadLimitService,
			AuditLogService auditLogService) {
		this.images = images;
		this.versions = versions;
		this.batches = batches;
		this.batchItems = batchItems;
		this.imageTags = imageTags;
		this.uploadBatchTags = uploadBatchTags;
		this.categories = categories;
		this.tagGroups = tagGroups;
		this.tags = tags;
		this.storage = storage;
		this.settings = settings;
		this.uploadLimitService = uploadLimitService;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	ImagePageResponse list(String keyword, String categoryId, String tagId, String status, int page, int size) {
		String query = keyword == null || keyword.isBlank() ? null : keyword.trim();
		String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
		int safePage = Math.max(1, page);
		int safeSize = Math.min(Math.max(1, size), 100);
		long total = images.countSearch(query, blankToNull(categoryId), blankToNull(tagId), statusFilter);
		List<ImageAsset> result = total == 0 ? List.of() : loadImages(images.searchIds(query, blankToNull(categoryId),
				blankToNull(tagId), statusFilter, (long) (safePage - 1) * safeSize, safeSize));
		return new ImagePageResponse(result.stream().map(ImageResponse::from).toList(), safePage, safeSize, total);
	}

	@Transactional(readOnly = true)
	ImageResponse detail(String id) {
		return ImageResponse.from(getImage(id));
	}

	@Transactional
	UploadBatchResponse upload(List<MultipartFile> files, String categoryId, List<String> tagIds) {
		if (files == null || files.isEmpty()) {
			throw new IllegalArgumentException("请选择至少一张图片");
		}
		UploadBatchResponse session = createUploadSession(
				new UploadSessionCreateRequest(files.size() == 1 ? "SINGLE" : "BATCH", categoryId, tagIds, files.size()));
		for (MultipartFile file : files) {
			session = stageUploadSessionItem(session.id(), file);
		}
		return confirmUploadSession(session.id());
	}

	@Transactional
	UploadBatchResponse createUploadSession(UploadSessionCreateRequest request) {
		int totalCount = request.totalCount();
		if (totalCount <= 0) {
			throw new IllegalArgumentException("请选择至少一张图片");
		}
		String mode = request.mode() == null || request.mode().isBlank() ? "BATCH" : request.mode().trim().toUpperCase();
		if (!mode.equals("SINGLE") && !mode.equals("BATCH")) {
			throw new IllegalArgumentException("上传模式不正确");
		}
		if (mode.equals("SINGLE") && totalCount != 1) {
			throw new IllegalArgumentException("单张上传只能选择 1 张图片");
		}
		UploadTaxonomy uploadTaxonomy = validateUploadTaxonomy(request.categoryId(), request.tagIds());
		UploadBatch batch = new UploadBatch(mode, totalCount, uploadTaxonomy.category().id(), uploadTaxonomy.tagIds());
		batches.insert(batch);
		replaceUploadBatchTags(batch);
		auditLogService.record("image.upload.session.create", "UPLOAD_BATCH", batch.id(),
				"{\"mode\":\"" + mode + "\",\"total\":" + totalCount + "}");
		return UploadBatchResponse.from(batch, List.of());
	}

	@Transactional
	UploadBatchResponse stageUploadSessionItem(String sessionId, MultipartFile file) {
		UploadBatch batch = getUploadSession(sessionId);
		ensureCanStage(batch);
		List<UploadBatchItem> existingItems = batchItems.selectByBatchIdOrdered(sessionId);
		if (existingItems.size() >= batch.totalCount()) {
			throw new IllegalArgumentException("上传文件数量已达到本次会话上限");
		}
		UploadBatchItem item = new UploadBatchItem(batch.id(), filename(file));
		batchItems.insert(item);
		stageItem(batch, item, file, existingItems);
		return uploadSession(batch.id());
	}

	@Transactional(readOnly = true)
	UploadBatchResponse batch(String id) {
		return uploadSession(id);
	}

	@Transactional(readOnly = true)
	UploadBatchResponse uploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(id));
	}

	@Transactional
	UploadBatchResponse retry(String batchId, String itemId) {
		UploadBatch batch = Optional.ofNullable(batches.selectById(batchId))
				.orElseThrow(() -> new IllegalArgumentException("上传批次不存在"));
		loadUploadBatchTags(batch);
		UploadBatchItem item = Optional.ofNullable(batchItems.selectById(itemId))
				.orElseThrow(() -> new IllegalArgumentException("上传文件不存在"));
		item.retryFailed("请重新选择原文件上传，系统不会保存失败文件的临时内容");
		batchItems.updateById(item);
		auditLogService.record("image.batch.item.retry", "UPLOAD_BATCH_ITEM", item.id(), "{}");
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(batch.id()));
	}

	@Transactional
	UploadBatchResponse retryUploadSessionItem(String sessionId, String itemId, MultipartFile file) {
		UploadBatch batch = getUploadSession(sessionId);
		ensureCanStage(batch);
		UploadBatchItem item = Optional.ofNullable(batchItems.selectById(itemId))
				.orElseThrow(() -> new IllegalArgumentException("上传文件不存在"));
		if (!item.batchId().equals(batch.id())) {
			throw new IllegalArgumentException("上传文件不属于当前会话");
		}
		if (!"FAILED".equals(item.status())) {
			throw new IllegalArgumentException("只有失败文件可以重试");
		}
		item.retrying(filename(file));
		List<UploadBatchItem> existingItems = batchItems.selectByBatchIdOrdered(sessionId).stream()
				.filter(existing -> !existing.id().equals(item.id()))
				.toList();
		stageItem(batch, item, file, existingItems);
		auditLogService.record("image.upload.session.item.retry", "UPLOAD_BATCH_ITEM", item.id(), "{}");
		return uploadSession(batch.id());
	}

	@Transactional
	UploadBatchResponse confirmUploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		if ("CONFIRMED".equals(batch.status())) {
			return uploadSession(id);
		}
		if ("CANCELLED".equals(batch.status()) || "EXPIRED".equals(batch.status())) {
			throw new IllegalArgumentException("上传会话已取消，不能确认入库");
		}
		UploadTaxonomy uploadTaxonomy = validateUploadTaxonomy(batch.categoryId(), batch.tagIds());
		List<UploadBatchItem> items = batchItems.selectByBatchIdOrdered(id);
		if (items.stream().noneMatch(item -> "STAGED".equals(item.status()) || "DUPLICATE".equals(item.status()))) {
			throw new IllegalArgumentException("没有可确认入库的上传文件");
		}
		for (UploadBatchItem item : items) {
			if ("STAGED".equals(item.status())) {
				var existing = images.findBySha256AndStatusNot(item.sha256(), "DELETED");
					if (existing.isPresent()) {
						storage.deleteQuietly(item.storedImage());
						item.duplicated(existing.get().id());
						batchItems.updateById(item);
						continue;
					}
				ImageAsset image = createImage(item.candidateImageId(), item.storedImage(), uploadTaxonomy);
				item.confirmed(image.id());
				batchItems.updateById(item);
			}
		}
		refreshUploadBatch(batch);
		batch.markConfirmed();
		batches.updateById(batch);
		auditLogService.record("image.upload.session.confirm", "UPLOAD_BATCH", batch.id(), "{\"total\":" + items.size() + "}");
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(batch.id()));
	}

	@Transactional
	UploadBatchResponse cancelUploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		if ("CONFIRMED".equals(batch.status())) {
			return uploadSession(id);
		}
		cancelUploadSession(batch, false);
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(batch.id()));
	}

	@Transactional
	ImageResponse update(String id, ImageUpdateRequest request) {
		ImageAsset image = getImage(id);
		String title = request.title() == null || request.title().isBlank() ? image.title() : request.title().trim();
		String status = request.status() == null || request.status().isBlank() ? image.status() : request.status().trim();
		Category category = findCategory(request.categoryId());
		Set<Tag> selectedTags = findTags(request.tagIds());
		image.updateMetadata(title, status);
		image.replaceTaxonomy(category, selectedTags);
		images.updateById(image);
		replaceImageTags(image);
		auditLogService.record("image.update", "IMAGE", image.id(), "{\"title\":\"" + escape(title) + "\"}");
		return ImageResponse.from(image);
	}

	@Transactional
	void delete(String id) {
		ImageAsset image = getImage(id);
		image.markDeleted();
		images.updateById(image);
		auditLogService.record("image.delete", "IMAGE", id, "{}");
	}

	@Transactional
	void batchDisable(ImageBatchRequest request) {
		for (ImageAsset image : getBatchImages(request)) {
			image.markDeleted();
			images.updateById(image);
			auditLogService.record("image.delete", "IMAGE", image.id(), "{\"batch\":true}");
		}
	}

	@Transactional
	void restore(String id) {
		ImageAsset image = getDeletedImage(id);
		image.restore();
		images.updateById(image);
		auditLogService.record("image.restore", "IMAGE", id, "{}");
	}

	@Transactional
	void batchRestore(ImageBatchRequest request) {
		for (ImageAsset image : getDeletedBatchImages(request)) {
			image.restore();
			images.updateById(image);
			auditLogService.record("image.restore", "IMAGE", image.id(), "{\"batch\":true}");
		}
	}

	@Transactional
	void purge(String id) {
		purgeImage(getDeletedImage(id), false);
	}

	@Transactional
	void batchPurge(ImageBatchRequest request) {
		for (ImageAsset image : getDeletedBatchImages(request)) {
			purgeImage(image, true);
		}
	}

	@Transactional
	int purgeDeleted() {
		List<ImageAsset> deletedImages = images.selectByStatus("DELETED");
		for (ImageAsset image : deletedImages) {
			purgeImage(image, true);
		}
		return deletedImages.size();
	}

	@Transactional
	int purgeExpiredDeletedImages() {
		if (!softDeleteCleanupEnabled()) {
			return 0;
		}
		int retentionDays = softDeleteRetentionDays();
		LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
		List<ImageAsset> expiredImages = images.selectByStatusAndDeletedAtBefore("DELETED", cutoff);
		for (ImageAsset image : expiredImages) {
			purgeImage(image, false, "{\"autoCleanup\":true,\"retentionDays\":" + retentionDays + "}");
		}
		if (!expiredImages.isEmpty()) {
			auditLogService.record("image.purge.retention.cleanup", "IMAGE", "DELETED",
					"{\"deleted\":" + expiredImages.size() + ",\"retentionDays\":" + retentionDays + "}");
		}
		return expiredImages.size();
	}

	@Transactional
	ObjectFile thumbnail(String id) {
		ImageAsset image = getRetainedImage(id);
		ImageVersion version = currentVersion(image);
		return new ObjectFile(version.bucket(), version.thumbnailObjectKey(), "image/png", image.originalFilename());
	}

	@Transactional
	ObjectFile preview(String id) {
		ImageAsset image = getRetainedImage(id);
		images.incrementViewCount(id);
		image.viewed();
		ImageVersion version = currentVersion(image);
		String quality = settings.get("preview.quality", "ORIGINAL");
		String key = switch (quality) {
			case "HIGH" -> version.highPreviewObjectKey();
			case "STANDARD" -> version.standardPreviewObjectKey();
			default -> version.originalObjectKey();
		};
		String mimeType = "ORIGINAL".equals(quality) ? version.mimeType() : "image/png";
		auditLogService.record("image.preview", "IMAGE", id, "{\"quality\":\"" + quality + "\"}");
		return new ObjectFile(version.bucket(), key, mimeType, image.originalFilename());
	}

	@Transactional
	ObjectFile download(String id) {
		ImageAsset image = getImage(id);
		images.incrementDownloadCount(id);
		image.downloaded();
		ImageVersion version = currentVersion(image);
		auditLogService.record("image.download", "IMAGE", id, "{}");
		return new ObjectFile(version.bucket(), version.originalObjectKey(), version.mimeType(), version.originalFilename());
	}

	@Transactional
	BatchDownloadFile batchDownload(ImageBatchRequest request) {
		List<ImageAsset> selectedImages = getBatchImages(request);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Map<String, Integer> usedNames = new HashMap<>();
		try (ZipOutputStream zip = new ZipOutputStream(output)) {
			for (ImageAsset image : selectedImages) {
				ImageVersion version = currentVersion(image);
				byte[] content = storage.read(version.bucket(), version.originalObjectKey());
				ZipEntry entry = new ZipEntry(uniqueZipFilename(version.originalFilename(), usedNames));
				zip.putNextEntry(entry);
					zip.write(content);
					zip.closeEntry();
					images.incrementDownloadCount(image.id());
					image.downloaded();
				auditLogService.record("image.download", "IMAGE", image.id(), "{\"batch\":true}");
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("批量下载生成失败", ex);
		}
		return new BatchDownloadFile("wallpaper-images-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".zip",
				output.toByteArray());
	}

	byte[] read(ObjectFile file) {
		return storage.read(file.bucket(), file.objectKey());
	}

	@Transactional(readOnly = true)
	Statistics statistics() {
		LocalDate today = LocalDate.now();
		LocalDate trendStart = today.minusDays(29);
		return new Statistics(images.countByStatusNot("DELETED"), images.countByCreatedAtAfterAndStatusNot(today.atStartOfDay(), "DELETED"),
				images.totalViews(), images.totalDownloads(), images.totalStorageBytes(), uploadTrend(trendStart, today),
				categoryDistribution(), topImagesByViews(), topImagesByDownloads());
	}

	private List<TrendPoint> uploadTrend(LocalDate startDate, LocalDate endDate) {
		Map<LocalDate, Long> countsByDay = new HashMap<>();
		for (ImageDailyCount row : images.countUploadsByDaySince(startDate.atStartOfDay())) {
			countsByDay.put(row.day(), row.total());
		}
		return startDate.datesUntil(endDate.plusDays(1))
				.map(date -> new TrendPoint(date, countsByDay.getOrDefault(date, 0L)))
				.toList();
	}

	private List<CategoryDistributionItem> categoryDistribution() {
		List<CategoryDistributionItem> items = images.countImagesByCategory().stream()
				.map(row -> new CategoryDistributionItem(row.categoryId(), row.name(), row.total()))
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		long uncategorized = images.countUncategorizedImages();
		if (uncategorized > 0) {
			items.add(new CategoryDistributionItem(null, "未分类", uncategorized));
		}
		return items;
	}

	private List<ImageRankingItem> topImagesByViews() {
		return images.selectTopViewed("DELETED", 5)
				.stream().map(ImageRankingItem::from).toList();
	}

	private List<ImageRankingItem> topImagesByDownloads() {
		return images.selectTopDownloaded("DELETED", 5)
				.stream().map(ImageRankingItem::from).toList();
	}

	private void stageItem(UploadBatch batch, UploadBatchItem item, MultipartFile file, List<UploadBatchItem> existingItems) {
		StoredImage stored = null;
		try {
			item.receivedSize(file.getSize());
			validateUploadSize(file, existingItems);
			String sha256 = sha256(file);
			var existingImage = images.findBySha256AndStatusNot(sha256, "DELETED");
			if (existingImage.isPresent()) {
				item.duplicated(existingImage.get().id(), sha256);
				batchItems.updateById(item);
			}
			else if (existingItems.stream().anyMatch(existing -> sha256.equals(existing.sha256())
					&& ("STAGED".equals(existing.status()) || "DUPLICATE".equals(existing.status())))) {
				item.duplicated(null, sha256);
				batchItems.updateById(item);
			}
			else {
				String candidateImageId = UUID.randomUUID().toString();
				stored = storage.store(file, candidateImageId);
				try {
					item.staged(candidateImageId, stored);
					batchItems.updateById(item);
				}
				catch (RuntimeException ex) {
					storage.deleteQuietly(stored);
					throw ex;
				}
			}
		}
		catch (Exception ex) {
			item.failed(ex.getMessage() == null ? "上传失败" : ex.getMessage());
			batchItems.updateById(item);
		}
		refreshUploadBatch(batch);
	}

	private void validateUploadSize(MultipartFile file, List<UploadBatchItem> existingItems) {
		UploadLimitSettings limits = uploadLimitService.current();
		long fileSize = file.getSize();
		if (fileSize > limits.maxFileSizeBytes()) {
			throw new IllegalArgumentException("文件大小超过单文件上限 " + limits.maxFileSizeMb() + " MB");
		}
		long acceptedSize = existingItems.stream()
				.mapToLong(UploadBatchItem::sizeBytes)
				.sum();
		if (acceptedSize + fileSize > limits.maxBatchSizeBytes()) {
			throw new IllegalArgumentException("本次上传总大小超过批量上传上限 " + limits.maxBatchSizeMb() + " MB");
		}
	}

	private ImageAsset createImage(String imageId, StoredImage stored, UploadTaxonomy uploadTaxonomy) {
		ImageAsset image = new ImageAsset(imageId, titleFrom(stored.originalFilename()), stored.originalFilename(), stored.sha256(), stored.mimeType(),
				stored.sizeBytes(), stored.width(), stored.height());
		image.replaceTaxonomy(uploadTaxonomy.category(), uploadTaxonomy.tags());
		images.insert(image);
		replaceImageTags(image);
		ImageVersion version = new ImageVersion(image.id(), 1, "UPLOAD", stored);
		versions.insert(version);
		image.setCurrentVersionId(version.id());
		images.updateById(image);
		auditLogService.record("image.upload", "IMAGE", image.id(), "{\"filename\":\"" + escape(stored.originalFilename()) + "\"}");
		return image;
	}

	private List<ImageAsset> getBatchImages(ImageBatchRequest request) {
		List<String> ids = request == null || request.ids() == null
				? List.of()
				: request.ids().stream()
						.filter(id -> id != null && !id.isBlank())
						.map(String::trim)
						.distinct()
						.toList();
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("请选择至少一张图片");
		}
		Map<String, ImageAsset> found = images.selectImagesByIds(ids).stream()
				.filter(image -> !"DELETED".equals(image.status()))
				.collect(java.util.stream.Collectors.toMap(ImageAsset::id, image -> image));
		List<String> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("部分图片不存在或已停用");
		}
		return ids.stream().map(found::get).toList();
	}

	private List<ImageAsset> getDeletedBatchImages(ImageBatchRequest request) {
		List<String> ids = request == null || request.ids() == null
				? List.of()
				: request.ids().stream()
						.filter(id -> id != null && !id.isBlank())
						.map(String::trim)
						.distinct()
						.toList();
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("请选择至少一张图片");
		}
		Map<String, ImageAsset> found = images.selectImagesByIds(ids).stream()
				.filter(image -> "DELETED".equals(image.status()))
				.collect(java.util.stream.Collectors.toMap(ImageAsset::id, image -> image));
		List<String> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("部分图片不存在或未停用");
		}
		return ids.stream().map(found::get).toList();
	}

	private void purgeImage(ImageAsset image, boolean batch) {
		purgeImage(image, batch, batch ? "{\"batch\":true}" : "{}");
	}

	private void purgeImage(ImageAsset image, boolean batch, String auditDetail) {
		List<ImageVersion> imageVersions = versions.selectByImageIdOrdered(image.id());
		for (ImageVersion version : imageVersions) {
			storage.delete(new StoredImage(version.originalFilename(), "", version.mimeType(), 0L, null, null,
					version.bucket(), version.originalObjectKey(), version.thumbnailObjectKey(), version.highPreviewObjectKey(),
					version.standardPreviewObjectKey()));
		}
		if (!imageVersions.isEmpty()) {
			versions.deleteVersionsByIds(imageVersions.stream().map(ImageVersion::id).toList());
		}
		imageTags.deleteByImageId(image.id());
		images.deleteById(image.id());
		auditLogService.record("image.purge", "IMAGE", image.id(), auditDetail);
	}

	private boolean softDeleteCleanupEnabled() {
		return Boolean.parseBoolean(settings.get(SoftDeleteCleanupSettings.CLEANUP_ENABLED, "false"));
	}

	private int softDeleteRetentionDays() {
		try {
			return Math.max(1, Integer.parseInt(settings.get(SoftDeleteCleanupSettings.RETENTION_DAYS, "180")));
		}
		catch (NumberFormatException ex) {
			return 180;
		}
	}

	private void refreshUploadBatch(UploadBatch batch) {
		List<UploadBatchItem> items = batchItems.selectByBatchIdOrdered(batch.id());
		int success = (int) items.stream().filter(item -> "STAGED".equals(item.status()) || "CONFIRMED".equals(item.status())).count();
		int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status())).count();
		int duplicate = (int) items.stream().filter(item -> "DUPLICATE".equals(item.status())).count();
		int processed = success + failed + duplicate;
		batch.refreshCounts(success, failed, duplicate, processed);
		batches.updateById(batch);
	}

	private UploadBatch getUploadSession(String id) {
		UploadBatch batch = Optional.ofNullable(batches.selectById(id))
				.orElseThrow(() -> new IllegalArgumentException("上传会话不存在"));
		loadUploadBatchTags(batch);
		return batch;
	}

	private void ensureCanStage(UploadBatch batch) {
		if (batch.isTerminal()) {
			throw new IllegalArgumentException("上传会话已结束");
		}
	}

	private void cancelUploadSession(UploadBatch batch, boolean expired) {
		List<UploadBatchItem> items = batchItems.selectByBatchIdOrdered(batch.id());
		for (UploadBatchItem item : items) {
			if (item.hasStoredObjects()) {
				storage.deleteQuietly(item.storedImage());
				item.cancelled();
				batchItems.updateById(item);
				continue;
			}
			if (!"DUPLICATE".equals(item.status()) && !"FAILED".equals(item.status())) {
				item.cancelled();
				batchItems.updateById(item);
			}
		}
		if (expired) {
			batch.markExpired();
			auditLogService.record("image.upload.session.expire", "UPLOAD_BATCH", batch.id(), "{}");
		}
		else {
			batch.markCancelled();
			auditLogService.record("image.upload.session.cancel", "UPLOAD_BATCH", batch.id(), "{}");
		}
		batches.updateById(batch);
	}

	private UploadTaxonomy validateUploadTaxonomy(String categoryId, List<String> tagIds) {
		if (categoryId == null || categoryId.isBlank()) {
			throw new IllegalArgumentException("上传图片必须选择分类");
		}
		if (tagIds == null || tagIds.stream().noneMatch(tagId -> tagId != null && !tagId.isBlank())) {
			throw new IllegalArgumentException("上传图片必须至少选择一个标签");
		}
		Category category = Optional.ofNullable(categories.selectById(categoryId.trim()))
				.orElseThrow(() -> new IllegalArgumentException("选择的分类不存在"));
		if (!category.enabled()) {
			throw new IllegalArgumentException("选择的分类已停用");
		}
		Set<String> requiredTagIds = tagIds.stream()
				.filter(tagId -> tagId != null && !tagId.isBlank())
				.map(String::trim)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<Tag> selectedTags = new LinkedHashSet<>(tags.selectBatchIds(requiredTagIds));
		if (selectedTags.size() != requiredTagIds.size()) {
			throw new IllegalArgumentException("选择的标签不存在");
		}
		validateSelectedTags(selectedTags);
		return new UploadTaxonomy(category, selectedTags);
	}

	private ImageAsset getImage(String id) {
		return Optional.ofNullable(images.selectById(id))
				.map(this::loadImageRelations)
				.filter(image -> !"DELETED".equals(image.status()))
				.orElseThrow(() -> new IllegalArgumentException("图片不存在"));
	}

	private ImageAsset getDeletedImage(String id) {
		return Optional.ofNullable(images.selectById(id))
				.map(this::loadImageRelations)
				.filter(image -> "DELETED".equals(image.status()))
				.orElseThrow(() -> new IllegalArgumentException("图片不存在或未停用"));
	}

	private ImageAsset getRetainedImage(String id) {
		return Optional.ofNullable(images.selectById(id))
				.map(this::loadImageRelations)
				.orElseThrow(() -> new IllegalArgumentException("图片不存在"));
	}

	private ImageVersion currentVersion(ImageAsset image) {
		if (image.currentVersionId() != null) {
			return Optional.ofNullable(versions.selectById(image.currentVersionId()))
					.orElseThrow(() -> new IllegalArgumentException("图片版本不存在"));
		}
		return versions.findLatestByImageId(image.id())
				.orElseThrow(() -> new IllegalArgumentException("图片版本不存在"));
	}

	private Category findCategory(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		Category category = Optional.ofNullable(categories.selectById(id.trim()))
				.orElseThrow(() -> new IllegalArgumentException("选择的分类不存在"));
		if (!category.enabled()) {
			throw new IllegalArgumentException("选择的分类已停用");
		}
		return category;
	}

	private Set<Tag> findTags(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		Set<String> requiredTagIds = ids.stream()
				.filter(id -> id != null && !id.isBlank())
				.map(String::trim)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		if (requiredTagIds.isEmpty()) {
			return Set.of();
		}
		Set<Tag> selectedTags = new LinkedHashSet<>(tags.selectBatchIds(requiredTagIds));
		if (selectedTags.size() != requiredTagIds.size()) {
			throw new IllegalArgumentException("选择的标签不存在");
		}
		validateSelectedTags(selectedTags);
		return selectedTags;
	}

	private void validateSelectedTags(Set<Tag> selectedTags) {
		if (selectedTags.isEmpty()) {
			return;
		}
		Map<String, TagGroup> groupsById = tagGroups.selectBatchIds(
						selectedTags.stream().map(Tag::groupId).distinct().toList())
				.stream()
				.collect(java.util.stream.Collectors.toMap(TagGroup::id, Function.identity()));
		boolean invalidTag = selectedTags.stream()
				.anyMatch(tag -> !tag.enabled() || !Optional.ofNullable(groupsById.get(tag.groupId()))
						.map(TagGroup::enabled)
						.orElse(false));
		if (invalidTag) {
			throw new IllegalArgumentException("选择的标签或标签组已停用");
		}
	}

	private static String sha256(MultipartFile file) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 不可用", ex);
		}
	}

	private static String filename(MultipartFile file) {
		return file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
				? "image"
				: file.getOriginalFilename();
	}

	private static String titleFrom(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot > 0 ? filename.substring(0, dot) : filename;
	}

	private static String uniqueZipFilename(String filename, Map<String, Integer> usedNames) {
		String cleaned = filename == null || filename.isBlank() ? "image" : filename.replace("\\", "/");
		int slash = cleaned.lastIndexOf('/');
		if (slash >= 0) {
			cleaned = cleaned.substring(slash + 1);
		}
		if (cleaned.isBlank()) {
			cleaned = "image";
		}
		String candidate = cleaned;
		int counter = 1;
		while (usedNames.containsKey(candidate)) {
			counter++;
			int dot = cleaned.lastIndexOf('.');
			candidate = dot > 0
					? cleaned.substring(0, dot) + "-" + counter + cleaned.substring(dot)
					: cleaned + "-" + counter;
		}
		usedNames.put(candidate, 1);
		return candidate;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private List<ImageAsset> loadImages(List<String> imageIds) {
		if (imageIds.isEmpty()) {
			return List.of();
		}
		Map<String, ImageAsset> imagesById = images.selectImagesByIds(imageIds).stream()
				.collect(java.util.stream.Collectors.toMap(ImageAsset::id, Function.identity()));
		List<ImageAsset> ordered = imageIds.stream()
				.map(imagesById::get)
				.filter(java.util.Objects::nonNull)
				.toList();
		loadImageRelations(ordered);
		return ordered;
	}

	private ImageAsset loadImageRelations(ImageAsset image) {
		loadImageRelations(List.of(image));
		return image;
	}

	private void loadImageRelations(List<ImageAsset> imageList) {
		if (imageList.isEmpty()) {
			return;
		}
		List<String> categoryIds = imageList.stream()
				.map(ImageAsset::categoryId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		Map<String, Category> categoriesById = categoryIds.isEmpty()
				? Map.of()
				: categories.selectBatchIds(categoryIds).stream()
						.collect(java.util.stream.Collectors.toMap(Category::id, Function.identity()));

		List<ImageTagLink> links = imageTags.selectByImageIds(imageList.stream().map(ImageAsset::id).toList());
		List<String> tagIds = links.stream().map(ImageTagLink::tagId).distinct().toList();
		Map<String, Tag> tagsById = tagIds.isEmpty()
				? Map.of()
				: tags.selectBatchIds(tagIds).stream()
						.collect(java.util.stream.Collectors.toMap(Tag::id, Function.identity()));
		Map<String, TagGroup> tagGroupsById = tagsById.isEmpty()
				? Map.of()
				: tagGroups.selectBatchIds(tagsById.values().stream().map(Tag::groupId).distinct().toList()).stream()
						.collect(java.util.stream.Collectors.toMap(TagGroup::id, Function.identity()));
		tagsById.values().forEach(tag -> tag.attachGroup(tagGroupsById.get(tag.groupId())));
		Map<String, Tag> visibleTagsById = tagsById.values().stream()
				.filter(tag -> tag.enabled() && Optional.ofNullable(tagGroupsById.get(tag.groupId()))
						.map(TagGroup::enabled)
						.orElse(false))
				.collect(java.util.stream.Collectors.toMap(Tag::id, Function.identity()));
		Map<String, List<ImageTagLink>> linksByImage = links.stream()
				.collect(java.util.stream.Collectors.groupingBy(ImageTagLink::imageId));

		for (ImageAsset image : imageList) {
			Set<Tag> assignedTags = linksByImage.getOrDefault(image.id(), List.of()).stream()
					.map(link -> visibleTagsById.get(link.tagId()))
					.filter(java.util.Objects::nonNull)
					.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			Category category = image.categoryId() == null ? null : categoriesById.get(image.categoryId());
			image.replaceTaxonomy(category, assignedTags);
		}
	}

	private void replaceImageTags(ImageAsset image) {
		imageTags.deleteByImageId(image.id());
		List<String> tagIds = image.tags().stream().map(Tag::id).toList();
		if (!tagIds.isEmpty()) {
			imageTags.insertBatch(image.id(), tagIds);
		}
	}

	private void replaceUploadBatchTags(UploadBatch batch) {
		uploadBatchTags.deleteByBatchId(batch.id());
		if (!batch.tagIds().isEmpty()) {
			uploadBatchTags.insertBatch(batch.id(), batch.tagIds());
		}
	}

	private void loadUploadBatchTags(UploadBatch batch) {
		batch.replaceTagIds(uploadBatchTags.selectTagIdsByBatchId(batch.id()));
	}

	@Transactional
	int expireUnconfirmedUploadSessions() {
		List<UploadBatch> expired = batches.selectExpired(
				List.of("CREATED", "STAGING", "STAGED", "PARTIAL_FAILED"), LocalDateTime.now());
		for (UploadBatch batch : expired) {
			loadUploadBatchTags(batch);
			cancelUploadSession(batch, true);
		}
		return expired.size();
	}

	@Transactional
	int cleanupOrphanImageObjects() {
		Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60);
		Set<String> referencedKeys = new HashSet<>();
		referencedKeys.addAll(versions.selectReferencedObjectKeys());
		referencedKeys.addAll(batchItems.selectReferencedObjectKeysByStatuses(List.of("PROCESSING", "STAGED")));
		referencedKeys.remove(null);
		List<ImageStorageService.StoredObject> orphanObjects = storage.listImageObjects().stream()
				.filter(object -> object.lastModified() == null || object.lastModified().isBefore(cutoff))
				.filter(object -> !referencedKeys.contains(object.key()))
				.toList();
		for (ImageStorageService.StoredObject object : orphanObjects) {
			storage.deleteObjectsQuietly(object.bucket(), List.of(object.key()));
		}
		if (!orphanObjects.isEmpty()) {
			auditLogService.record("image.storage.orphan.cleanup", "STORAGE", "images",
					"{\"deleted\":" + orphanObjects.size() + "}");
		}
		return orphanObjects.size();
	}

	record ObjectFile(String bucket, String objectKey, String mimeType, String filename) {
	}

	record BatchDownloadFile(String filename, byte[] content) {
	}

	record Statistics(long imageTotal, long todayUploaded, long viewCount, long downloadCount, long storageBytes,
			List<TrendPoint> uploadTrend, List<CategoryDistributionItem> categoryDistribution,
			List<ImageRankingItem> topViewedImages, List<ImageRankingItem> topDownloadedImages) {
	}

	record TrendPoint(LocalDate date, long count) {
	}

	record CategoryDistributionItem(String categoryId, String name, long count) {
	}

	record ImageRankingItem(String id, String title, long viewCount, long downloadCount) {
		static ImageRankingItem from(ImageAsset image) {
			return new ImageRankingItem(image.id(), image.title(), image.viewCount(), image.downloadCount());
		}
	}

	private record UploadTaxonomy(Category category, Set<Tag> tags) {
		List<String> tagIds() {
			return tags.stream().map(Tag::id).toList();
		}
	}
}
