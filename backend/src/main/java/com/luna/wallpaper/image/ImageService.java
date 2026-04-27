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
import com.luna.wallpaper.image.ImageDtos.ImageVersionResponse;
import com.luna.wallpaper.image.ImageDtos.UploadBatchResponse;
import com.luna.wallpaper.image.ImageDtos.UploadSessionCreateRequest;
import com.luna.wallpaper.settings.ImageVersionSettings;
import com.luna.wallpaper.settings.SoftDeleteCleanupSettings;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.settings.UploadLimitService;
import com.luna.wallpaper.settings.UploadLimitService.UploadLimitSettings;
import com.luna.wallpaper.settings.WatermarkSettings;
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
		ImageStatus statusFilter = ImageStatus.parseOrNull(status);
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
				Map.of("mode", mode, "total", totalCount));
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
		auditLogService.record("image.batch.item.retry", "UPLOAD_BATCH_ITEM", item.id(), Map.of());
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
		if (!item.status().isRetryable()) {
			throw new IllegalArgumentException("只有失败文件可以重试");
		}
		item.retrying(filename(file));
		List<UploadBatchItem> existingItems = batchItems.selectByBatchIdOrdered(sessionId).stream()
				.filter(existing -> !existing.id().equals(item.id()))
				.toList();
		stageItem(batch, item, file, existingItems);
		auditLogService.record("image.upload.session.item.retry", "UPLOAD_BATCH_ITEM", item.id(), Map.of());
		return uploadSession(batch.id());
	}

	@Transactional
	UploadBatchResponse confirmUploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		if (batch.status().isConfirmed()) {
			return uploadSession(id);
		}
		if (batch.status().isClosedWithoutConfirm()) {
			throw new IllegalArgumentException("上传会话已取消，不能确认入库");
		}
		UploadTaxonomy uploadTaxonomy = validateUploadTaxonomy(batch.categoryId(), batch.tagIds());
		List<UploadBatchItem> items = batchItems.selectByBatchIdOrdered(id);
		if (items.stream().noneMatch(item -> item.status().isConfirmable())) {
			throw new IllegalArgumentException("没有可确认入库的上传文件");
		}
		for (UploadBatchItem item : items) {
			if (item.status() == UploadBatchItemStatus.STAGED) {
				var existing = images.findBySha256AndStatusNot(item.sha256(), ImageStatus.DELETED);
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
		auditLogService.record("image.upload.session.confirm", "UPLOAD_BATCH", batch.id(), Map.of("total", items.size()));
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(batch.id()));
	}

	@Transactional
	UploadBatchResponse cancelUploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		if (batch.status().isConfirmed()) {
			return uploadSession(id);
		}
		cancelUploadSession(batch, false);
		return UploadBatchResponse.from(batch, batchItems.selectByBatchIdOrdered(batch.id()));
	}

	@Transactional
	ImageResponse update(String id, ImageUpdateRequest request) {
		ImageAsset image = getImage(id);
		String title = request.title() == null || request.title().isBlank() ? image.title() : request.title().trim();
		ImageStatus status = request.status() == null || request.status().isBlank()
				? image.status()
				: ImageStatus.parse(request.status());
		Category category = findCategory(request.categoryId());
		Set<Tag> selectedTags = findTags(request.tagIds());
		image.updateMetadata(title, status);
		image.replaceTaxonomy(category, selectedTags);
		images.updateById(image);
		replaceImageTags(image);
		auditLogService.record("image.update", "IMAGE", image.id(), Map.of("title", title, "status", status.name()));
		return ImageResponse.from(image);
	}

	@Transactional
	ImageResponse editImage(String id, MultipartFile file, String operations) {
		ImageAsset image = getImage(id);
		validateEditFile(file);
		StoredImage stored = storage.store(file, image.id());
		try {
			ImageVersion sourceVersion = currentVersion(image);
			int nextVersionNo = versions.findLatestByImageId(image.id())
					.map(ImageVersion::versionNo)
					.orElse(sourceVersion.versionNo()) + 1;
			versions.clearCurrentFlag(image.id());
			ImageVersion version = new ImageVersion(image.id(), nextVersionNo, "EDIT", stored,
					sourceVersion.id());
			versions.insert(version);
			image.replaceCurrentFile(stored);
			image.setCurrentVersionId(version.id());
			images.updateById(image);
			auditLogService.record("image.edit.image", "IMAGE", image.id(),
					Map.of("sourceVersionId", sourceVersion.id(), "versionId", version.id(),
							"operations", operations == null ? "" : operations));
			cleanupExcessVersions(image.id(), false);
			return ImageResponse.from(image);
		}
		catch (RuntimeException ex) {
			storage.deleteQuietly(stored);
			throw ex;
		}
	}

	@Transactional
	void delete(String id) {
		ImageAsset image = getImage(id);
		image.markDeleted();
		images.updateById(image);
		auditLogService.record("image.delete", "IMAGE", id, Map.of());
	}

	@Transactional(readOnly = true)
	List<ImageVersionResponse> versions(String id) {
		ImageAsset image = getRetainedImage(id);
		ImageVersion current = currentVersion(image);
		return versions.selectByImageIdOrdered(image.id()).stream()
				.map(version -> ImageVersionResponse.from(version, current.id()))
				.toList();
	}

	@Transactional(readOnly = true)
	ObjectFile versionThumbnail(String id, String versionId) {
		ImageAsset image = getRetainedImage(id);
		ImageVersion version = getImageVersion(image.id(), versionId);
		return new ObjectFile(version.bucket(), version.thumbnailObjectKey(), "image/png", version.originalFilename());
	}

	@Transactional(readOnly = true)
	ObjectFile versionPreview(String id, String versionId) {
		ImageAsset image = getRetainedImage(id);
		ImageVersion version = getImageVersion(image.id(), versionId);
		String quality = previewQuality();
		return applyWatermark(versionPreviewObject(version, quality), watermarkPreviewEnabled());
	}

	@Transactional
	ImageResponse restoreVersion(String id, String versionId) {
		ImageAsset image = getImage(id);
		ImageVersion version = getImageVersion(image.id(), versionId);
		versions.clearCurrentFlag(image.id());
		versions.markCurrent(version.id());
		image.replaceCurrentVersion(version);
		images.updateById(image);
		auditLogService.record("image.version.restore", "IMAGE", image.id(),
				Map.of("versionId", version.id(), "versionNo", version.versionNo()));
		return ImageResponse.from(loadImageRelations(image));
	}

	@Transactional
	void deleteVersion(String id, String versionId) {
		ImageAsset image = getImage(id);
		ImageVersion current = currentVersion(image);
		if (current.id().equals(versionId)) {
			throw new IllegalArgumentException("当前版本不能删除");
		}
		ImageVersion version = getImageVersion(image.id(), versionId);
		deleteVersionObjectsAndRecord(version);
		auditLogService.record("image.version.delete", "IMAGE", image.id(),
				Map.of("versionId", version.id(), "versionNo", version.versionNo()));
	}

	@Transactional
	void batchDisable(ImageBatchRequest request) {
		for (ImageAsset image : getBatchImages(request)) {
			image.markDeleted();
			images.updateById(image);
			auditLogService.record("image.delete", "IMAGE", image.id(), Map.of("batch", true));
		}
	}

	@Transactional
	void restore(String id) {
		ImageAsset image = getDeletedImage(id);
		image.restore();
		images.updateById(image);
		auditLogService.record("image.restore", "IMAGE", id, Map.of());
	}

	@Transactional
	void batchRestore(ImageBatchRequest request) {
		for (ImageAsset image : getDeletedBatchImages(request)) {
			image.restore();
			images.updateById(image);
			auditLogService.record("image.restore", "IMAGE", image.id(), Map.of("batch", true));
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
		List<ImageAsset> deletedImages = images.selectByStatus(ImageStatus.DELETED);
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
		List<ImageAsset> expiredImages = images.selectByStatusAndDeletedAtBefore(ImageStatus.DELETED, cutoff);
		for (ImageAsset image : expiredImages) {
			purgeImage(image, false, Map.of("autoCleanup", true, "retentionDays", retentionDays));
		}
		if (!expiredImages.isEmpty()) {
			auditLogService.record("image.purge.retention.cleanup", "IMAGE", ImageStatus.DELETED.name(),
					Map.of("deleted", expiredImages.size(), "retentionDays", retentionDays));
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
		String quality = previewQuality();
		ObjectFile preview = versionPreviewObject(version, quality);
		auditLogService.record("image.preview", "IMAGE", id, Map.of("quality", quality));
		return applyWatermark(preview, watermarkPreviewEnabled());
	}

	@Transactional
	ObjectFile download(String id) {
		ImageAsset image = getImage(id);
		images.incrementDownloadCount(id);
		image.downloaded();
		ImageVersion version = currentVersion(image);
		auditLogService.record("image.download", "IMAGE", id, Map.of());
		return applyWatermark(new ObjectFile(version.bucket(), version.originalObjectKey(), version.mimeType(), version.originalFilename()),
				watermarkEnabled());
	}

	@Transactional
	ObjectFile editSource(String id) {
		ImageAsset image = getImage(id);
		ImageVersion version = currentVersion(image);
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
				DownloadContent download = downloadContent(version);
				ZipEntry entry = new ZipEntry(uniqueZipFilename(download.filename(), usedNames));
				zip.putNextEntry(entry);
				zip.write(download.content());
				zip.closeEntry();
				images.incrementDownloadCount(image.id());
				image.downloaded();
				auditLogService.record("image.download", "IMAGE", image.id(), Map.of("batch", true));
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("批量下载生成失败", ex);
		}
		return new BatchDownloadFile("wallpaper-images-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".zip",
				output.toByteArray());
	}

	byte[] read(ObjectFile file) {
		if (file.content() != null) {
			return file.content();
		}
		return storage.read(file.bucket(), file.objectKey());
	}

	@Transactional(readOnly = true)
	Statistics statistics() {
		LocalDate today = LocalDate.now();
		LocalDate trendStart = today.minusDays(29);
		return new Statistics(images.countByStatusNot(ImageStatus.DELETED),
				images.countByCreatedAtAfterAndStatusNot(today.atStartOfDay(), ImageStatus.DELETED),
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
		return images.selectTopViewed(ImageStatus.DELETED, 5)
				.stream().map(ImageRankingItem::from).toList();
	}

	private List<ImageRankingItem> topImagesByDownloads() {
		return images.selectTopDownloaded(ImageStatus.DELETED, 5)
				.stream().map(ImageRankingItem::from).toList();
	}

	private void stageItem(UploadBatch batch, UploadBatchItem item, MultipartFile file, List<UploadBatchItem> existingItems) {
		StoredImage stored = null;
		try {
			item.receivedSize(file.getSize());
			validateUploadSize(file, existingItems);
			String sha256 = sha256(file);
			var existingImage = images.findBySha256AndStatusNot(sha256, ImageStatus.DELETED);
			if (existingImage.isPresent()) {
				item.duplicated(existingImage.get().id(), sha256);
				batchItems.updateById(item);
			}
			else if (existingItems.stream().anyMatch(existing -> sha256.equals(existing.sha256())
					&& existing.status().isConfirmable())) {
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

	private void validateEditFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择编辑后的图片文件");
		}
		UploadLimitSettings limits = uploadLimitService.current();
		if (file.getSize() > limits.maxFileSizeBytes()) {
			throw new IllegalArgumentException("文件大小超过单文件上限 " + limits.maxFileSizeMb() + " MB");
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
		auditLogService.record("image.upload", "IMAGE", image.id(), Map.of("filename", stored.originalFilename()));
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
				.filter(image -> image.status().isRetained())
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
				.filter(image -> image.status().isDeleted())
				.collect(java.util.stream.Collectors.toMap(ImageAsset::id, image -> image));
		List<String> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("部分图片不存在或未停用");
		}
		return ids.stream().map(found::get).toList();
	}

	private void purgeImage(ImageAsset image, boolean batch) {
		purgeImage(image, batch, batch ? Map.of("batch", true) : Map.of());
	}

	private void purgeImage(ImageAsset image, boolean batch, Object auditDetail) {
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
		int success = (int) items.stream().filter(item -> item.status().countsAsSuccess()).count();
		int failed = (int) items.stream().filter(item -> item.status().countsAsFailure()).count();
		int duplicate = (int) items.stream().filter(item -> item.status().countsAsDuplicate()).count();
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
			if (item.status().canBecomeCancelledWithoutStoredObjects()) {
				item.cancelled();
				batchItems.updateById(item);
			}
		}
		if (expired) {
			batch.markExpired();
			auditLogService.record("image.upload.session.expire", "UPLOAD_BATCH", batch.id(), Map.of());
		}
		else {
			batch.markCancelled();
			auditLogService.record("image.upload.session.cancel", "UPLOAD_BATCH", batch.id(), Map.of());
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
				.filter(image -> image.status().isRetained())
				.orElseThrow(() -> new IllegalArgumentException("图片不存在"));
	}

	private ImageAsset getDeletedImage(String id) {
		return Optional.ofNullable(images.selectById(id))
				.map(this::loadImageRelations)
				.filter(image -> image.status().isDeleted())
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

	private ImageVersion getImageVersion(String imageId, String versionId) {
		return Optional.ofNullable(versions.selectByImageIdAndId(imageId, versionId))
				.orElseThrow(() -> new IllegalArgumentException("图片版本不存在"));
	}

	private void deleteVersionObjectsAndRecord(ImageVersion version) {
		storage.delete(storedImage(version));
		versions.deleteVersionById(version.id());
	}

	private StoredImage storedImage(ImageVersion version) {
		return new StoredImage(version.originalFilename(), version.sha256(), version.mimeType(), version.sizeBytes(),
				version.width(), version.height(), version.bucket(), version.originalObjectKey(), version.thumbnailObjectKey(),
				version.highPreviewObjectKey(), version.standardPreviewObjectKey());
	}

	private ObjectFile versionPreviewObject(ImageVersion version, String quality) {
		return switch (quality) {
			case "HIGH" -> new ObjectFile(version.bucket(), version.highPreviewObjectKey(), "image/png", version.originalFilename());
			case "STANDARD" -> new ObjectFile(version.bucket(), version.standardPreviewObjectKey(), "image/png", version.originalFilename());
			default -> new ObjectFile(version.bucket(), version.originalObjectKey(), version.mimeType(), version.originalFilename());
		};
	}

	private String previewQuality() {
		return settings.get("preview.quality", "ORIGINAL");
	}

	private int maxRetainedImageVersions() {
		try {
			return Math.max(1, Integer.parseInt(settings.get(ImageVersionSettings.MAX_RETAINED,
					String.valueOf(ImageVersionSettings.DEFAULT_MAX_RETAINED))));
		}
		catch (NumberFormatException ex) {
			return ImageVersionSettings.DEFAULT_MAX_RETAINED;
		}
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

	private ObjectFile applyWatermark(ObjectFile file, boolean enabled) {
		if (!enabled) {
			return file;
		}
		ImageStorageService.WatermarkedImage watermarked = storage.watermark(storage.read(file.bucket(), file.objectKey()),
				watermarkOptions());
		if (!"image/png".equals(watermarked.mimeType())) {
			return file;
		}
		return new ObjectFile(null, null, watermarked.mimeType(), watermarkedFilename(file.filename()), watermarked.bytes());
	}

	private DownloadContent downloadContent(ImageVersion version) {
		if (!watermarkEnabled()) {
			return new DownloadContent(version.originalFilename(), storage.read(version.bucket(), version.originalObjectKey()));
		}
		ImageStorageService.WatermarkedImage watermarked = storage.watermark(
				storage.read(version.bucket(), version.originalObjectKey()), watermarkOptions());
		if (!"image/png".equals(watermarked.mimeType())) {
			return new DownloadContent(version.originalFilename(), watermarked.bytes());
		}
		return new DownloadContent(watermarkedFilename(version.originalFilename()), watermarked.bytes());
	}

	private boolean watermarkEnabled() {
		return Boolean.parseBoolean(settings.get(WatermarkSettings.ENABLED, "true"));
	}

	private boolean watermarkPreviewEnabled() {
		return Boolean.parseBoolean(settings.get(WatermarkSettings.PREVIEW_ENABLED, "false"));
	}

	private ImageStorageService.WatermarkOptions watermarkOptions() {
		return new ImageStorageService.WatermarkOptions(
				settings.get(WatermarkSettings.TEXT, WatermarkSettings.DEFAULT_TEXT),
				choiceSetting(WatermarkSettings.MODE, WatermarkSettings.DEFAULT_MODE, "CORNER", "TILED"),
				choiceSetting(WatermarkSettings.POSITION, WatermarkSettings.DEFAULT_POSITION,
						"TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
						"CENTER_LEFT", "CENTER", "CENTER_RIGHT",
						"BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"),
				intRangeSetting(WatermarkSettings.OPACITY_PERCENT, WatermarkSettings.DEFAULT_OPACITY_PERCENT,
						WatermarkSettings.MIN_OPACITY_PERCENT, WatermarkSettings.MAX_OPACITY_PERCENT),
				choiceSetting(WatermarkSettings.TILE_DENSITY, WatermarkSettings.DEFAULT_TILE_DENSITY,
						"SPARSE", "NORMAL", "DENSE"));
	}

	private String choiceSetting(String key, String defaultValue, String... allowedValues) {
		String value = settings.get(key, defaultValue);
		for (String allowedValue : allowedValues) {
			if (allowedValue.equals(value)) {
				return value;
			}
		}
		return defaultValue;
	}

	private int intRangeSetting(String key, int defaultValue, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(settings.get(key, String.valueOf(defaultValue)))));
		}
		catch (NumberFormatException ex) {
			return defaultValue;
		}
	}

	private static String watermarkedFilename(String filename) {
		String cleaned = filename == null || filename.isBlank() ? "image" : filename.replace("\\", "/");
		int slash = cleaned.lastIndexOf('/');
		if (slash >= 0) {
			cleaned = cleaned.substring(slash + 1);
		}
		int dot = cleaned.lastIndexOf('.');
		String base = dot > 0 ? cleaned.substring(0, dot) : cleaned;
		return (base.isBlank() ? "image" : base) + "-watermarked.png";
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
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
		List<UploadBatch> expired = batches.selectExpired(UploadBatchStatus.expirableStatuses(), LocalDateTime.now());
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
		referencedKeys.addAll(batchItems.selectReferencedObjectKeysByStatuses(
				UploadBatchItemStatus.referencedObjectStatuses()));
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
					Map.of("deleted", orphanObjects.size()));
		}
		return orphanObjects.size();
	}

	@Transactional
	int cleanupExcessImageVersions() {
		int maxRetained = maxRetainedImageVersions();
		int deleted = 0;
		for (String imageId : versions.selectImageIdsExceedingRetainedLimit(maxRetained)) {
			deleted += cleanupExcessVersions(imageId, true);
		}
		if (deleted > 0) {
			auditLogService.record("image.version.retention.cleanup", "IMAGE", "versions",
					Map.of("deleted", deleted, "maxRetained", maxRetained));
		}
		return deleted;
	}

	private int cleanupExcessVersions(String imageId, boolean scheduled) {
		int maxRetained = maxRetainedImageVersions();
		List<ImageVersion> imageVersions = Optional.ofNullable(versions.selectByImageIdOrdered(imageId)).orElse(List.of());
		if (imageVersions.size() <= maxRetained) {
			return 0;
		}
		String currentId = Optional.ofNullable(images.selectById(imageId))
				.map(ImageAsset::currentVersionId)
				.orElseGet(() -> imageVersions.stream()
						.filter(ImageVersion::currentFlag)
						.findFirst()
						.map(ImageVersion::id)
						.orElse(null));
		int remaining = imageVersions.size();
		int deleted = 0;
		for (int index = imageVersions.size() - 1; index >= 0 && remaining > maxRetained; index--) {
			ImageVersion version = imageVersions.get(index);
			if (version.id().equals(currentId)) {
				continue;
			}
			deleteVersionObjectsAndRecord(version);
			remaining--;
			deleted++;
		}
		if (deleted > 0 && !scheduled) {
			auditLogService.record("image.version.retention.cleanup", "IMAGE", imageId,
					Map.of("deleted", deleted, "maxRetained", maxRetained));
		}
		return deleted;
	}

	record ObjectFile(String bucket, String objectKey, String mimeType, String filename, byte[] content) {
		ObjectFile(String bucket, String objectKey, String mimeType, String filename) {
			this(bucket, objectKey, mimeType, filename, null);
		}
	}

	record BatchDownloadFile(String filename, byte[] content) {
	}

	private record DownloadContent(String filename, byte[] content) {
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
