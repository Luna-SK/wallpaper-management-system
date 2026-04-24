package com.luna.wallpaper.image;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.image.ImageDtos.ImageResponse;
import com.luna.wallpaper.image.ImageDtos.ImageUpdateRequest;
import com.luna.wallpaper.image.ImageDtos.UploadBatchResponse;
import com.luna.wallpaper.image.ImageDtos.UploadSessionCreateRequest;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.taxonomy.Category;
import com.luna.wallpaper.taxonomy.CategoryRepository;
import com.luna.wallpaper.taxonomy.Tag;
import com.luna.wallpaper.taxonomy.TagRepository;

@Service
class ImageService {

	private final ImageAssetRepository images;
	private final ImageVersionRepository versions;
	private final UploadBatchRepository batches;
	private final UploadBatchItemRepository batchItems;
	private final CategoryRepository categories;
	private final TagRepository tags;
	private final ImageStorageService storage;
	private final SystemSettingService settings;
	private final AuditLogService auditLogService;

	ImageService(ImageAssetRepository images, ImageVersionRepository versions, UploadBatchRepository batches,
			UploadBatchItemRepository batchItems, CategoryRepository categories, TagRepository tags,
			ImageStorageService storage, SystemSettingService settings, AuditLogService auditLogService) {
		this.images = images;
		this.versions = versions;
		this.batches = batches;
		this.batchItems = batchItems;
		this.categories = categories;
		this.tags = tags;
		this.storage = storage;
		this.settings = settings;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	List<ImageResponse> list(String keyword, String categoryId, String tagId, int limit) {
		String query = keyword == null || keyword.isBlank() ? null : keyword.trim();
		return images.search(query, blankToNull(categoryId), blankToNull(tagId), PageRequest.of(0, Math.min(limit, 200)))
				.stream().map(ImageResponse::from).toList();
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
		UploadBatch batch = batches.save(new UploadBatch(mode, totalCount, uploadTaxonomy.category().id(), uploadTaxonomy.tagIds()));
		auditLogService.record("image.upload.session.create", "UPLOAD_BATCH", batch.id(),
				"{\"mode\":\"" + mode + "\",\"total\":" + totalCount + "}");
		return UploadBatchResponse.from(batch, List.of());
	}

	@Transactional
	UploadBatchResponse stageUploadSessionItem(String sessionId, MultipartFile file) {
		UploadBatch batch = getUploadSession(sessionId);
		ensureCanStage(batch);
		List<UploadBatchItem> existingItems = batchItems.findByBatchIdOrderByCreatedAtAsc(sessionId);
		if (existingItems.size() >= batch.totalCount()) {
			throw new IllegalArgumentException("上传文件数量已达到本次会话上限");
		}
		UploadBatchItem item = batchItems.save(new UploadBatchItem(batch.id(), filename(file)));
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
		return UploadBatchResponse.from(batch, batchItems.findByBatchIdOrderByCreatedAtAsc(id));
	}

	@Transactional
	UploadBatchResponse retry(String batchId, String itemId) {
		UploadBatch batch = batches.findById(batchId).orElseThrow(() -> new IllegalArgumentException("上传批次不存在"));
		UploadBatchItem item = batchItems.findById(itemId).orElseThrow(() -> new IllegalArgumentException("上传文件不存在"));
		item.retryFailed("请重新选择原文件上传，系统不会保存失败文件的临时内容");
		auditLogService.record("image.batch.item.retry", "UPLOAD_BATCH_ITEM", item.id(), "{}");
		return UploadBatchResponse.from(batch, batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id()));
	}

	@Transactional
	UploadBatchResponse retryUploadSessionItem(String sessionId, String itemId, MultipartFile file) {
		UploadBatch batch = getUploadSession(sessionId);
		ensureCanStage(batch);
		UploadBatchItem item = batchItems.findById(itemId).orElseThrow(() -> new IllegalArgumentException("上传文件不存在"));
		if (!item.batchId().equals(batch.id())) {
			throw new IllegalArgumentException("上传文件不属于当前会话");
		}
		if (!"FAILED".equals(item.status())) {
			throw new IllegalArgumentException("只有失败文件可以重试");
		}
		item.retrying(filename(file));
		List<UploadBatchItem> existingItems = batchItems.findByBatchIdOrderByCreatedAtAsc(sessionId).stream()
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
		List<UploadBatchItem> items = batchItems.findByBatchIdOrderByCreatedAtAsc(id);
		if (items.stream().noneMatch(item -> "STAGED".equals(item.status()) || "DUPLICATE".equals(item.status()))) {
			throw new IllegalArgumentException("没有可确认入库的上传文件");
		}
		for (UploadBatchItem item : items) {
			if ("STAGED".equals(item.status())) {
				var existing = images.findBySha256AndStatusNot(item.sha256(), "DELETED");
				if (existing.isPresent()) {
					storage.deleteQuietly(item.storedImage());
					item.duplicated(existing.get().id());
					continue;
				}
				ImageAsset image = createImage(item.candidateImageId(), item.storedImage(), uploadTaxonomy);
				item.confirmed(image.id());
			}
		}
		refreshUploadBatch(batch);
		batch.markConfirmed();
		auditLogService.record("image.upload.session.confirm", "UPLOAD_BATCH", batch.id(), "{\"total\":" + items.size() + "}");
		return UploadBatchResponse.from(batch, batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id()));
	}

	@Transactional
	UploadBatchResponse cancelUploadSession(String id) {
		UploadBatch batch = getUploadSession(id);
		if ("CONFIRMED".equals(batch.status())) {
			return uploadSession(id);
		}
		cancelUploadSession(batch, false);
		return UploadBatchResponse.from(batch, batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id()));
	}

	@Transactional
	ImageResponse update(String id, ImageUpdateRequest request) {
		ImageAsset image = getImage(id);
		String title = request.title() == null || request.title().isBlank() ? image.title() : request.title().trim();
		String status = request.status() == null || request.status().isBlank() ? image.status() : request.status().trim();
		image.updateMetadata(title, status);
		image.replaceTaxonomy(findCategories(request.categoryIds()), findTags(request.tagIds()));
		auditLogService.record("image.update", "IMAGE", image.id(), "{\"title\":\"" + escape(title) + "\"}");
		return ImageResponse.from(image);
	}

	@Transactional
	void delete(String id) {
		ImageAsset image = getImage(id);
		image.markDeleted();
		auditLogService.record("image.delete", "IMAGE", id, "{}");
	}

	@Transactional
	ObjectFile thumbnail(String id) {
		ImageAsset image = getImage(id);
		ImageVersion version = currentVersion(image);
		return new ObjectFile(version.bucket(), version.thumbnailObjectKey(), "image/png", image.originalFilename());
	}

	@Transactional
	ObjectFile preview(String id) {
		ImageAsset image = getImage(id);
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
		image.downloaded();
		ImageVersion version = currentVersion(image);
		auditLogService.record("image.download", "IMAGE", id, "{}");
		return new ObjectFile(version.bucket(), version.originalObjectKey(), version.mimeType(), version.originalFilename());
	}

	byte[] read(ObjectFile file) {
		return storage.read(file.bucket(), file.objectKey());
	}

	@Transactional(readOnly = true)
	Statistics statistics() {
		LocalDateTime today = LocalDate.now().atStartOfDay();
		return new Statistics(images.countByStatusNot("DELETED"), images.countByCreatedAtAfterAndStatusNot(today, "DELETED"),
				images.totalViews(), images.totalDownloads(), images.totalStorageBytes());
	}

	private void stageItem(UploadBatch batch, UploadBatchItem item, MultipartFile file, List<UploadBatchItem> existingItems) {
		StoredImage stored = null;
		try {
			String sha256 = sha256(file);
			var existingImage = images.findBySha256AndStatusNot(sha256, "DELETED");
			if (existingImage.isPresent()) {
				item.duplicated(existingImage.get().id(), sha256);
			}
			else if (existingItems.stream().anyMatch(existing -> sha256.equals(existing.sha256())
					&& ("STAGED".equals(existing.status()) || "DUPLICATE".equals(existing.status())))) {
				item.duplicated(null, sha256);
			}
			else {
				String candidateImageId = UUID.randomUUID().toString();
				stored = storage.store(file, candidateImageId);
				try {
					item.staged(candidateImageId, stored);
					batchItems.flush();
				}
				catch (RuntimeException ex) {
					storage.deleteQuietly(stored);
					throw ex;
				}
			}
		}
		catch (Exception ex) {
			item.failed(ex.getMessage() == null ? "上传失败" : ex.getMessage());
		}
		refreshUploadBatch(batch);
	}

	private ImageAsset createImage(String imageId, StoredImage stored, UploadTaxonomy uploadTaxonomy) {
		ImageAsset image = new ImageAsset(imageId, titleFrom(stored.originalFilename()), stored.originalFilename(), stored.sha256(), stored.mimeType(),
				stored.sizeBytes(), stored.width(), stored.height());
		image.replaceTaxonomy(Set.of(uploadTaxonomy.category()), uploadTaxonomy.tags());
		image = images.save(image);
		ImageVersion version = versions.save(new ImageVersion(image.id(), 1, "UPLOAD", stored));
		image.setCurrentVersionId(version.id());
		auditLogService.record("image.upload", "IMAGE", image.id(), "{\"filename\":\"" + escape(stored.originalFilename()) + "\"}");
		return image;
	}

	private void refreshUploadBatch(UploadBatch batch) {
		List<UploadBatchItem> items = batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id());
		int success = (int) items.stream().filter(item -> "STAGED".equals(item.status()) || "CONFIRMED".equals(item.status())).count();
		int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status())).count();
		int duplicate = (int) items.stream().filter(item -> "DUPLICATE".equals(item.status())).count();
		int processed = success + failed + duplicate;
		batch.refreshCounts(success, failed, duplicate, processed);
	}

	private UploadBatch getUploadSession(String id) {
		return batches.findById(id).orElseThrow(() -> new IllegalArgumentException("上传会话不存在"));
	}

	private void ensureCanStage(UploadBatch batch) {
		if (batch.isTerminal()) {
			throw new IllegalArgumentException("上传会话已结束");
		}
	}

	private void cancelUploadSession(UploadBatch batch, boolean expired) {
		List<UploadBatchItem> items = batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id());
		for (UploadBatchItem item : items) {
			if (item.hasStoredObjects()) {
				storage.deleteQuietly(item.storedImage());
				item.cancelled();
				continue;
			}
			if (!"DUPLICATE".equals(item.status()) && !"FAILED".equals(item.status())) {
				item.cancelled();
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
	}

	private UploadTaxonomy validateUploadTaxonomy(String categoryId, List<String> tagIds) {
		if (categoryId == null || categoryId.isBlank()) {
			throw new IllegalArgumentException("上传图片必须选择分类");
		}
		if (tagIds == null || tagIds.stream().noneMatch(tagId -> tagId != null && !tagId.isBlank())) {
			throw new IllegalArgumentException("上传图片必须至少选择一个标签");
		}
		Category category = categories.findById(categoryId.trim())
				.orElseThrow(() -> new IllegalArgumentException("选择的分类不存在"));
		if (!category.enabled()) {
			throw new IllegalArgumentException("选择的分类已停用");
		}
		Set<String> requiredTagIds = tagIds.stream()
				.filter(tagId -> tagId != null && !tagId.isBlank())
				.map(String::trim)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<Tag> selectedTags = new LinkedHashSet<>(tags.findByIdIn(requiredTagIds));
		if (selectedTags.size() != requiredTagIds.size()) {
			throw new IllegalArgumentException("选择的标签不存在");
		}
		boolean invalidTag = selectedTags.stream()
				.anyMatch(tag -> !tag.enabled() || !tag.categoryId().equals(category.id()));
		if (invalidTag) {
			throw new IllegalArgumentException("选择的标签必须属于当前分类且处于启用状态");
		}
		return new UploadTaxonomy(category, selectedTags);
	}

	private ImageAsset getImage(String id) {
		return images.findById(id)
				.filter(image -> !"DELETED".equals(image.status()))
				.orElseThrow(() -> new IllegalArgumentException("图片不存在"));
	}

	private ImageVersion currentVersion(ImageAsset image) {
		if (image.currentVersionId() != null) {
			return versions.findById(image.currentVersionId())
					.orElseThrow(() -> new IllegalArgumentException("图片版本不存在"));
		}
		return versions.findFirstByImageIdOrderByVersionNoDesc(image.id())
				.orElseThrow(() -> new IllegalArgumentException("图片版本不存在"));
	}

	private Set<Category> findCategories(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		return new LinkedHashSet<>(categories.findAllById(ids));
	}

	private Set<Tag> findTags(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		return new LinkedHashSet<>(tags.findByIdIn(ids));
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

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	@Transactional
	int expireUnconfirmedUploadSessions() {
		List<UploadBatch> expired = batches.findByStatusInAndExpiresAtBefore(
				List.of("CREATED", "STAGING", "STAGED", "PARTIAL_FAILED"), LocalDateTime.now());
		for (UploadBatch batch : expired) {
			cancelUploadSession(batch, true);
		}
		return expired.size();
	}

	@Transactional
	int cleanupOrphanImageObjects() {
		Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60);
		Set<String> referencedKeys = new HashSet<>();
		for (ImageVersion version : versions.findAll()) {
			referencedKeys.add(version.originalObjectKey());
			referencedKeys.add(version.thumbnailObjectKey());
			referencedKeys.add(version.highPreviewObjectKey());
			referencedKeys.add(version.standardPreviewObjectKey());
		}
		for (UploadBatchItem item : batchItems.findByStatusIn(List.of("PROCESSING", "STAGED"))) {
			referencedKeys.add(item.originalObjectKey());
			referencedKeys.add(item.thumbnailObjectKey());
			referencedKeys.add(item.highPreviewObjectKey());
			referencedKeys.add(item.standardPreviewObjectKey());
		}
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

	record Statistics(long imageTotal, long todayUploaded, long viewCount, long downloadCount, long storageBytes) {
	}

	private record UploadTaxonomy(Category category, Set<Tag> tags) {
		List<String> tagIds() {
			return tags.stream().map(Tag::id).toList();
		}
	}
}
