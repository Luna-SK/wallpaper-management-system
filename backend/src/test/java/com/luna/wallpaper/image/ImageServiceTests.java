package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.taxonomy.CategoryRepository;
import com.luna.wallpaper.taxonomy.TagRepository;

class ImageServiceTests {

	private final ImageAssetRepository images = mock(ImageAssetRepository.class);
	private final ImageService service = new ImageService(images, mock(ImageVersionRepository.class),
			mock(UploadBatchRepository.class), mock(UploadBatchItemRepository.class), mock(CategoryRepository.class),
			mock(TagRepository.class), mock(ImageStorageService.class), mock(SystemSettingService.class),
			mock(AuditLogService.class));

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

	private ImageAsset image(String id, String title, long viewCount, long downloadCount) {
		ImageAsset image = new ImageAsset(id, title, title + ".jpg", id + "-sha", "image/jpeg", 1024, 100, 100);
		ReflectionTestUtils.setField(image, "viewCount", viewCount);
		ReflectionTestUtils.setField(image, "downloadCount", downloadCount);
		return image;
	}
}
