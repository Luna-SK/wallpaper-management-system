package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.List;

import com.luna.wallpaper.taxonomy.Category;
import com.luna.wallpaper.taxonomy.Tag;

public final class ImageDtos {
	private ImageDtos() {
	}

	public record ImageUpdateRequest(String title, String status, String categoryId, List<String> tagIds) {
	}

	public record ImageBatchRequest(List<String> ids) {
	}

	public record ImagePageResponse(List<ImageResponse> items, int page, int size, long total) {
	}

	public record ImagePurgeResponse(int count) {
	}

	public record ImageVersionResponse(String id, int versionNo, boolean current, String operationType,
			String originalFilename, String mimeType, long sizeBytes, Integer width, Integer height,
			LocalDateTime createdAt) {
		static ImageVersionResponse from(ImageVersion version, String currentVersionId) {
			return new ImageVersionResponse(version.id(), version.versionNo(),
					version.id().equals(currentVersionId),
					version.operationType(), version.originalFilename(), version.mimeType(), version.sizeBytes(),
					version.width(), version.height(), version.createdAt());
		}
	}

	public record UploadSessionCreateRequest(String mode, String categoryId, List<String> tagIds, int totalCount) {
	}

	public record ImageResponse(String id, String title, String originalFilename, String mimeType, long sizeBytes,
			Integer width, Integer height, String status, long viewCount, long downloadCount, CategoryBrief category,
			List<TagBrief> tags, LocalDateTime createdAt) {
		static ImageResponse from(ImageAsset image) {
			return new ImageResponse(image.id(), image.title(), image.originalFilename(), image.mimeType(), image.sizeBytes(),
					image.width(), image.height(), image.status().name(), image.viewCount(), image.downloadCount(),
					image.category() == null ? null : CategoryBrief.from(image.category()),
					image.tags().stream().map(TagBrief::from).toList(), image.createdAt());
		}
	}

	public record CategoryBrief(String id, String code, String name) {
		static CategoryBrief from(Category category) {
			return new CategoryBrief(category.id(), category.code(), category.name());
		}
	}

	public record TagBrief(String id, String categoryId, String groupId, String groupName, String name) {
		static TagBrief from(Tag tag) {
			return new TagBrief(tag.id(), null, tag.groupId(), tag.groupName(), tag.name());
		}
	}

	public record UploadBatchResponse(String id, String status, int totalCount, int successCount, int failedCount,
			int duplicateCount, int progressPercent, LocalDateTime createdAt, LocalDateTime finishedAt,
			String mode, String categoryId, List<String> tagIds, LocalDateTime expiresAt, LocalDateTime confirmedAt,
			List<UploadBatchItemResponse> items) {
		static UploadBatchResponse from(UploadBatch batch, List<UploadBatchItem> items) {
			return new UploadBatchResponse(batch.id(), batch.status().name(), batch.totalCount(), batch.successCount(),
					batch.failedCount(), batch.duplicateCount(), batch.progressPercent(), batch.createdAt(),
					batch.finishedAt(), batch.mode(), batch.categoryId(), batch.tagIds(), batch.expiresAt(), batch.confirmedAt(),
					items.stream().map(UploadBatchItemResponse::from).toList());
		}
	}

	public record UploadBatchItemResponse(String id, String imageId, String candidateImageId, String originalFilename,
			String status, int progressPercent, int retryCount, String errorMessage) {
		static UploadBatchItemResponse from(UploadBatchItem item) {
			return new UploadBatchItemResponse(item.id(), item.imageId(), item.candidateImageId(), item.originalFilename(),
					item.status().name(), item.progressPercent(), item.retryCount(), item.errorMessage());
		}
	}
}
