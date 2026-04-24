package edu.wzut.wallpaper.image;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import edu.wzut.wallpaper.audit.AuditLogService;
import edu.wzut.wallpaper.image.ImageDtos.ImageResponse;
import edu.wzut.wallpaper.image.ImageDtos.ImageUpdateRequest;
import edu.wzut.wallpaper.image.ImageDtos.UploadBatchResponse;
import edu.wzut.wallpaper.settings.SystemSettingService;
import edu.wzut.wallpaper.taxonomy.Category;
import edu.wzut.wallpaper.taxonomy.CategoryRepository;
import edu.wzut.wallpaper.taxonomy.Tag;
import edu.wzut.wallpaper.taxonomy.TagRepository;

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
		UploadBatch batch = batches.save(new UploadBatch(files.size()));
		List<UploadBatchItem> items = files.stream()
				.map(file -> batchItems.save(new UploadBatchItem(batch.id(), filename(file))))
				.toList();
		int success = 0;
		int failed = 0;
		int duplicate = 0;
		for (int index = 0; index < files.size(); index++) {
			MultipartFile file = files.get(index);
			UploadBatchItem item = items.get(index);
			try {
				String sha256 = sha256(file);
				var existing = images.findBySha256AndStatusNot(sha256, "DELETED");
				if (existing.isPresent()) {
					item.duplicated(existing.get().id());
					duplicate++;
				}
				else {
					ImageAsset image = createImage(file, sha256, categoryId, tagIds);
					item.succeeded(image.id());
					success++;
				}
			}
			catch (Exception firstFailure) {
				try {
					String sha256 = sha256(file);
					ImageAsset image = createImage(file, sha256, categoryId, tagIds);
					item.succeeded(image.id());
					success++;
				}
				catch (Exception retryFailure) {
					item.retryFailed(retryFailure.getMessage());
					failed++;
				}
			}
			batch.refreshCounts(success, failed, duplicate, index + 1);
		}
		auditLogService.record("image.batch.upload", "UPLOAD_BATCH", batch.id(), "{\"total\":" + files.size() + "}");
		return UploadBatchResponse.from(batch, batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id()));
	}

	@Transactional(readOnly = true)
	UploadBatchResponse batch(String id) {
		UploadBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("上传批次不存在"));
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

	private ImageAsset createImage(MultipartFile file, String sha256, String categoryId, List<String> tagIds) {
		StoredImage stored = storage.store(file, java.util.UUID.randomUUID().toString());
		ImageAsset image = new ImageAsset(titleFrom(filename(file)), filename(file), sha256, stored.mimeType(),
				stored.sizeBytes(), stored.width(), stored.height());
		image.replaceTaxonomy(findCategories(categoryId == null || categoryId.isBlank() ? List.of() : List.of(categoryId)),
				findTags(tagIds));
		image = images.save(image);
		ImageVersion version = versions.save(new ImageVersion(image.id(), 1, "UPLOAD", stored));
		image.setCurrentVersionId(version.id());
		auditLogService.record("image.upload", "IMAGE", image.id(), "{\"filename\":\"" + escape(filename(file)) + "\"}");
		return image;
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

	record ObjectFile(String bucket, String objectKey, String mimeType, String filename) {
	}

	record Statistics(long imageTotal, long todayUploaded, long viewCount, long downloadCount, long storageBytes) {
	}
}
