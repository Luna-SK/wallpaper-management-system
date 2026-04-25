package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.settings.UploadLimitService;
import com.luna.wallpaper.settings.UploadLimitService.UploadLimitSettings;
import com.luna.wallpaper.taxonomy.CategoryRepository;
import com.luna.wallpaper.taxonomy.TagRepository;

class ImageServiceTests {

	private final ImageAssetRepository images = mock(ImageAssetRepository.class);
	private final ImageVersionRepository versions = mock(ImageVersionRepository.class);
	private final UploadBatchRepository batches = mock(UploadBatchRepository.class);
	private final UploadBatchItemRepository batchItems = mock(UploadBatchItemRepository.class);
	private final ImageStorageService storage = mock(ImageStorageService.class);
	private final SystemSettingService settings = mock(SystemSettingService.class);
	private final UploadLimitService uploadLimitService = mock(UploadLimitService.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final ImageService service = new ImageService(images, versions,
			batches, batchItems, mock(CategoryRepository.class), mock(TagRepository.class), storage,
			settings, uploadLimitService, auditLogService);

	@Test
	void statisticsIncludesTrendDistributionAndRankings() {
		LocalDate today = LocalDate.now();
		LocalDate trendStart = today.minusDays(29);
		ImageAsset viewed = image("image-1", "热门预览", 12, 4);
		ImageAsset downloaded = image("image-2", "热门下载", 5, 9);
		when(images.countByStatusNot("DELETED")).thenReturn(42L);
		when(images.countByCreatedAtAfterAndStatusNot(today.atStartOfDay(), "DELETED")).thenReturn(3L);
		when(images.totalViews()).thenReturn(120L);
		when(images.totalDownloads()).thenReturn(30L);
		when(images.totalStorageBytes()).thenReturn(2048L);
		when(images.countUploadsByDaySince(trendStart.atStartOfDay())).thenReturn(List.<Object[]>of(
				new Object[] { Date.valueOf(trendStart.plusDays(1)), 2L },
				new Object[] { Date.valueOf(today), 5L }));
		when(images.countImagesByCategory()).thenReturn(List.<Object[]>of(new Object[] { "cat-1", "风景", 7L }));
		when(images.countUncategorizedImages()).thenReturn(1L);
		when(images.findByStatusNotOrderByViewCountDescCreatedAtDesc("DELETED", PageRequest.of(0, 5)))
				.thenReturn(List.of(viewed));
		when(images.findByStatusNotOrderByDownloadCountDescCreatedAtDesc("DELETED", PageRequest.of(0, 5)))
				.thenReturn(List.of(downloaded));

		ImageService.Statistics statistics = service.statistics();

		assertThat(statistics.imageTotal()).isEqualTo(42);
		assertThat(statistics.todayUploaded()).isEqualTo(3);
		assertThat(statistics.viewCount()).isEqualTo(120);
		assertThat(statistics.downloadCount()).isEqualTo(30);
		assertThat(statistics.storageBytes()).isEqualTo(2048);
		assertThat(statistics.uploadTrend()).hasSize(30);
		assertThat(statistics.uploadTrend().get(0)).isEqualTo(new ImageService.TrendPoint(trendStart, 0));
		assertThat(statistics.uploadTrend().get(1)).isEqualTo(new ImageService.TrendPoint(trendStart.plusDays(1), 2));
		assertThat(statistics.uploadTrend().get(29)).isEqualTo(new ImageService.TrendPoint(today, 5));
		assertThat(statistics.categoryDistribution()).containsExactly(
				new ImageService.CategoryDistributionItem("cat-1", "风景", 7),
				new ImageService.CategoryDistributionItem(null, "未分类", 1));
		assertThat(statistics.topViewedImages()).containsExactly(
				new ImageService.ImageRankingItem("image-1", "热门预览", 12, 4));
		assertThat(statistics.topDownloadedImages()).containsExactly(
				new ImageService.ImageRankingItem("image-2", "热门下载", 5, 9));
	}

	@Test
	void stageUploadSessionItemFailsWhenFileExceedsBusinessFileLimit() {
		UploadBatch batch = new UploadBatch("SINGLE", 1, "category-1", List.of("tag-1"));
		List<UploadBatchItem> items = new ArrayList<>();
		when(uploadLimitService.current()).thenReturn(new UploadLimitSettings(1, 10, 50, 500));
		when(batches.findById(batch.id())).thenReturn(Optional.of(batch));
		when(batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id())).thenAnswer(invocation -> List.copyOf(items));
		when(batchItems.save(any(UploadBatchItem.class))).thenAnswer(invocation -> {
			UploadBatchItem item = invocation.getArgument(0);
			items.add(item);
			return item;
		});

		var file = new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[2 * 1024 * 1024]);
		var response = service.stageUploadSessionItem(batch.id(), file);

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().status()).isEqualTo("FAILED");
		assertThat(response.items().getFirst().errorMessage()).contains("文件大小超过单文件上限 1 MB");
		verify(storage, never()).store(any(), anyString());
	}

	@Test
	void stageUploadSessionItemFailsWhenBatchTotalExceedsBusinessLimit() {
		UploadBatch batch = new UploadBatch("BATCH", 2, "category-1", List.of("tag-1"));
		UploadBatchItem existing = new UploadBatchItem(batch.id(), "existing.jpg");
		existing.receivedSize(1536L * 1024L);
		List<UploadBatchItem> items = new ArrayList<>(List.of(existing));
		when(uploadLimitService.current()).thenReturn(new UploadLimitSettings(2, 2, 50, 500));
		when(batches.findById(batch.id())).thenReturn(Optional.of(batch));
		when(batchItems.findByBatchIdOrderByCreatedAtAsc(batch.id())).thenAnswer(invocation -> List.copyOf(items));
		when(batchItems.save(any(UploadBatchItem.class))).thenAnswer(invocation -> {
			UploadBatchItem item = invocation.getArgument(0);
			items.add(item);
			return item;
		});

		var file = new MockMultipartFile("file", "second.jpg", "image/jpeg", new byte[1024 * 1024]);
		var response = service.stageUploadSessionItem(batch.id(), file);

		assertThat(response.items()).hasSize(2);
		assertThat(response.items().get(1).status()).isEqualTo("FAILED");
		assertThat(response.items().get(1).errorMessage()).contains("本次上传总大小超过批量上传上限 2 MB");
		verify(storage, never()).store(any(), anyString());
	}

	@Test
	void purgeExpiredDeletedImagesDoesNothingWhenCleanupDisabled() {
		when(settings.get("soft_delete.cleanup.enabled", "false")).thenReturn("false");

		int purged = service.purgeExpiredDeletedImages();

		assertThat(purged).isZero();
		verify(images, never()).findByStatusAndDeletedAtBefore(anyString(), any());
	}

	@Test
	void purgeExpiredDeletedImagesPurgesExpiredDeletedImagesWhenEnabled() {
		ImageAsset expired = image("image-expired", "到期停用", 0, 0);
		ReflectionTestUtils.setField(expired, "status", "DELETED");
		ReflectionTestUtils.setField(expired, "deletedAt", LocalDateTime.now().minusDays(8));
		when(settings.get("soft_delete.cleanup.enabled", "false")).thenReturn("true");
		when(settings.get("soft_delete.retention_days", "180")).thenReturn("7");
		when(images.findByStatusAndDeletedAtBefore(anyString(), any())).thenReturn(List.of(expired));
		when(versions.findByImageIdOrderByVersionNoDesc(expired.id())).thenReturn(List.of());

		int purged = service.purgeExpiredDeletedImages();

		assertThat(purged).isEqualTo(1);
		verify(images).delete(expired);
		verify(auditLogService).record("image.purge.retention.cleanup", "IMAGE", "DELETED",
				"{\"deleted\":1,\"retentionDays\":7}");
	}

	private ImageAsset image(String id, String title, long viewCount, long downloadCount) {
		ImageAsset image = new ImageAsset(id, title, title + ".jpg", id + "-sha", "image/jpeg", 1024, 100, 100);
		ReflectionTestUtils.setField(image, "viewCount", viewCount);
		ReflectionTestUtils.setField(image, "downloadCount", downloadCount);
		return image;
	}
}
