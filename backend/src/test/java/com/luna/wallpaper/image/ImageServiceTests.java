package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.settings.SystemSettingService;
import com.luna.wallpaper.settings.UploadLimitService;
import com.luna.wallpaper.settings.UploadLimitService.UploadLimitSettings;
import com.luna.wallpaper.taxonomy.CategoryMapper;
import com.luna.wallpaper.taxonomy.Tag;
import com.luna.wallpaper.taxonomy.TagGroup;
import com.luna.wallpaper.taxonomy.TagGroupMapper;
import com.luna.wallpaper.taxonomy.TagMapper;

class ImageServiceTests {

	private final ImageAssetMapper images = mock(ImageAssetMapper.class);
	private final ImageVersionMapper versions = mock(ImageVersionMapper.class);
	private final UploadBatchMapper batches = mock(UploadBatchMapper.class);
	private final UploadBatchItemMapper batchItems = mock(UploadBatchItemMapper.class);
	private final ImageTagMapper imageTags = mock(ImageTagMapper.class);
	private final UploadBatchTagMapper uploadBatchTags = mock(UploadBatchTagMapper.class);
	private final CategoryMapper categories = mock(CategoryMapper.class);
	private final TagGroupMapper tagGroups = mock(TagGroupMapper.class);
	private final TagMapper tags = mock(TagMapper.class);
	private final ImageStorageService storage = mock(ImageStorageService.class);
	private final SystemSettingService settings = mock(SystemSettingService.class);
	private final UploadLimitService uploadLimitService = mock(UploadLimitService.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final ImageService service = new ImageService(images, versions,
			batches, batchItems, imageTags, uploadBatchTags, categories, tagGroups, tags, storage,
			settings, uploadLimitService, auditLogService);

	@BeforeEach
	void setUp() {
		when(settings.get("watermark.enabled", "true")).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get("watermark.text", "仅供授权使用")).thenAnswer(invocation -> invocation.getArgument(1));
	}

	@Test
	void statisticsIncludesTrendDistributionAndRankings() {
		LocalDate today = LocalDate.now();
		LocalDate trendStart = today.minusDays(29);
		ImageAsset viewed = image("image-1", "热门预览", 12, 4);
		ImageAsset downloaded = image("image-2", "热门下载", 5, 9);
		when(images.countByStatusNot(ImageStatus.DELETED)).thenReturn(42L);
		when(images.countByCreatedAtAfterAndStatusNot(today.atStartOfDay(), ImageStatus.DELETED)).thenReturn(3L);
		when(images.totalViews()).thenReturn(120L);
		when(images.totalDownloads()).thenReturn(30L);
		when(images.totalStorageBytes()).thenReturn(2048L);
		when(images.countUploadsByDaySince(trendStart.atStartOfDay())).thenReturn(List.of(
				dailyCount(trendStart.plusDays(1), 2L),
				dailyCount(today, 5L)));
		when(images.countImagesByCategory()).thenReturn(List.of(categoryCount("cat-1", "风景", 7L)));
		when(images.countUncategorizedImages()).thenReturn(1L);
		when(images.selectTopViewed(ImageStatus.DELETED, 5)).thenReturn(List.of(viewed));
		when(images.selectTopDownloaded(ImageStatus.DELETED, 5)).thenReturn(List.of(downloaded));

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
	void listHandlesDeletedImagesWithoutCategory() {
		ImageAsset deleted = image("image-deleted", "已停用未分类", 0, 0);
		ReflectionTestUtils.setField(deleted, "status", ImageStatus.DELETED);
		when(images.countSearch(null, null, null, ImageStatus.DELETED)).thenReturn(1L);
		when(images.searchIds(null, null, null, ImageStatus.DELETED, 0L, 20)).thenReturn(List.of(deleted.id()));
		when(images.selectImagesByIds(List.of(deleted.id()))).thenReturn(List.of(deleted));
		when(imageTags.selectByImageIds(List.of(deleted.id()))).thenReturn(List.of());

		var page = service.list(null, null, null, "DELETED", 1, 20);

		assertThat(page.items()).hasSize(1);
		assertThat(page.items().getFirst().category()).isNull();
		assertThat(page.items().getFirst().status()).isEqualTo("DELETED");
		verify(categories, never()).selectBatchIds(any());
	}

	@Test
	void listHidesDisabledTagsAndTagsUnderDisabledGroups() {
		ImageAsset image = image("image-1", "墙布", 0, 0);
		Tag visibleTag = tag("tag-visible", "group-active", "可见标签", true);
		Tag disabledTag = tag("tag-disabled", "group-active", "已停用标签", false);
		Tag hiddenByGroupTag = tag("tag-hidden-group", "group-disabled", "停用组标签", true);
		TagGroup activeGroup = tagGroup("group-active", "启用组", true);
		TagGroup disabledGroup = tagGroup("group-disabled", "停用组", false);
		when(images.countSearch(null, null, null, null)).thenReturn(1L);
		when(images.searchIds(null, null, null, null, 0L, 20)).thenReturn(List.of(image.id()));
		when(images.selectImagesByIds(List.of(image.id()))).thenReturn(List.of(image));
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of(
				imageTagLink(image.id(), visibleTag.id()),
				imageTagLink(image.id(), disabledTag.id()),
				imageTagLink(image.id(), hiddenByGroupTag.id())));
		when(tags.selectBatchIds(List.of(visibleTag.id(), disabledTag.id(), hiddenByGroupTag.id())))
				.thenReturn(List.of(visibleTag, disabledTag, hiddenByGroupTag));
		when(tagGroups.selectBatchIds(any())).thenReturn(List.of(activeGroup, disabledGroup));

		var page = service.list(null, null, null, null, 1, 20);

		assertThat(page.items()).hasSize(1);
		assertThat(page.items().getFirst().tags())
				.extracting(ImageDtos.TagBrief::id)
				.containsExactly(visibleTag.id());
		assertThat(page.items().getFirst().tags().getFirst().groupName()).isEqualTo(activeGroup.name());
	}

	@Test
	void stageUploadSessionItemFailsWhenFileExceedsBusinessFileLimit() {
		UploadBatch batch = new UploadBatch("SINGLE", 1, "category-1", List.of("tag-1"));
		List<UploadBatchItem> items = new ArrayList<>();
		when(uploadLimitService.current()).thenReturn(new UploadLimitSettings(1, 10, 50, 500));
		when(batches.selectById(batch.id())).thenReturn(batch);
		when(uploadBatchTags.selectTagIdsByBatchId(batch.id())).thenReturn(batch.tagIds());
		when(batchItems.selectByBatchIdOrdered(batch.id())).thenAnswer(invocation -> List.copyOf(items));
		doAnswer(invocation -> {
			UploadBatchItem item = invocation.getArgument(0);
			items.add(item);
			return 1;
		}).when(batchItems).insert(any(UploadBatchItem.class));

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
		when(batches.selectById(batch.id())).thenReturn(batch);
		when(uploadBatchTags.selectTagIdsByBatchId(batch.id())).thenReturn(batch.tagIds());
		when(batchItems.selectByBatchIdOrdered(batch.id())).thenAnswer(invocation -> List.copyOf(items));
		doAnswer(invocation -> {
			UploadBatchItem item = invocation.getArgument(0);
			items.add(item);
			return 1;
		}).when(batchItems).insert(any(UploadBatchItem.class));

		var file = new MockMultipartFile("file", "second.jpg", "image/jpeg", new byte[1024 * 1024]);
		var response = service.stageUploadSessionItem(batch.id(), file);

		assertThat(response.items()).hasSize(2);
		assertThat(response.items().get(1).status()).isEqualTo("FAILED");
		assertThat(response.items().get(1).errorMessage()).contains("本次上传总大小超过批量上传上限 2 MB");
		verify(storage, never()).store(any(), anyString());
	}

	@Test
	void editImageStoresNewVersionAndUpdatesCurrentMetadata() {
		ImageAsset image = image("image-edit", "待编辑", 0, 0);
		StoredImage original = new StoredImage(image.originalFilename(), image.sha256(), image.mimeType(), image.sizeBytes(),
				image.width(), image.height(), "bucket", "original-key", "thumb-key", "high-key", "standard-key");
		ImageVersion current = new ImageVersion(image.id(), 1, "UPLOAD", original);
		StoredImage edited = new StoredImage("待编辑-edited.png", "edited-sha", "image/png", 2048, 80, 120,
				"bucket", "edited-original-key", "edited-thumb-key", "edited-high-key", "edited-standard-key");
		when(images.selectById(image.id())).thenReturn(image);
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of());
		when(versions.findLatestByImageId(image.id())).thenReturn(Optional.of(current));
		when(uploadLimitService.current()).thenReturn(new UploadLimitSettings(10, 100, 50, 500));
		when(storage.store(any(), anyString())).thenReturn(edited);
		ArgumentCaptor<ImageVersion> insertedVersion = ArgumentCaptor.forClass(ImageVersion.class);

		MockMultipartFile file = new MockMultipartFile("file", "edited.png", "image/png", new byte[1024]);
		var response = service.editImage(image.id(), file, "{\"rotation\":90}");

		assertThat(response.originalFilename()).isEqualTo("待编辑-edited.png");
		assertThat(response.mimeType()).isEqualTo("image/png");
		assertThat(response.width()).isEqualTo(80);
		assertThat(response.height()).isEqualTo(120);
		verify(versions).clearCurrentFlag(image.id());
		verify(versions).insert(insertedVersion.capture());
		assertThat(insertedVersion.getValue().sourceVersionId()).isEqualTo(current.id());
		verify(images).updateById(image);
		verify(auditLogService).record("image.edit.image", "IMAGE", image.id(), java.util.Map.of(
				"sourceVersionId", current.id(),
				"versionId", insertedVersion.getValue().id(),
				"operations", "{\"rotation\":90}"));
	}

	@Test
	void restoreVersionSwitchesCurrentVersionAndUpdatesImageMetadata() {
		ImageAsset image = image("image-restore-version", "当前图", 0, 0);
		StoredImage currentStored = new StoredImage("current.png", "current-sha", "image/png", 2048, 200, 100,
				"bucket", "current-original", "current-thumb", "current-high", "current-standard");
		StoredImage previousStored = new StoredImage("previous.jpg", "previous-sha", "image/jpeg", 1024, 80, 60,
				"bucket", "previous-original", "previous-thumb", "previous-high", "previous-standard");
		ImageVersion current = new ImageVersion(image.id(), 2, "EDIT", currentStored);
		ImageVersion previous = new ImageVersion(image.id(), 1, "UPLOAD", previousStored);
		image.replaceCurrentVersion(current);
		when(images.selectById(image.id())).thenReturn(image);
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of());
		when(versions.selectById(current.id())).thenReturn(current);
		when(versions.selectByImageIdAndId(image.id(), previous.id())).thenReturn(previous);

		var response = service.restoreVersion(image.id(), previous.id());

		assertThat(response.originalFilename()).isEqualTo("previous.jpg");
		assertThat(response.mimeType()).isEqualTo("image/jpeg");
		assertThat(response.width()).isEqualTo(80);
		assertThat(response.height()).isEqualTo(60);
		verify(versions).clearCurrentFlag(image.id());
		verify(versions).markCurrent(previous.id());
		verify(images).updateById(image);
		verify(auditLogService).record("image.version.restore", "IMAGE", image.id(),
				java.util.Map.of("versionId", previous.id(), "versionNo", 1));
	}

	@Test
	void deleteVersionRejectsCurrentVersion() {
		ImageAsset image = image("image-current-version", "当前版本", 0, 0);
		StoredImage stored = new StoredImage("current.png", "current-sha", "image/png", 2048, 200, 100,
				"bucket", "current-original", "current-thumb", "current-high", "current-standard");
		ImageVersion current = new ImageVersion(image.id(), 1, "UPLOAD", stored);
		image.replaceCurrentVersion(current);
		when(images.selectById(image.id())).thenReturn(image);
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of());
		when(versions.selectById(current.id())).thenReturn(current);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteVersion(image.id(), current.id()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("当前版本不能删除");

		verify(storage, never()).delete(any());
		verify(versions, never()).deleteVersionById(anyString());
	}

	@Test
	void deleteVersionRemovesNonCurrentVersionObjectsAndRecord() {
		ImageAsset image = image("image-delete-version", "删除版本", 0, 0);
		StoredImage currentStored = new StoredImage("current.png", "current-sha", "image/png", 2048, 200, 100,
				"bucket", "current-original", "current-thumb", "current-high", "current-standard");
		StoredImage previousStored = new StoredImage("previous.jpg", "previous-sha", "image/jpeg", 1024, 80, 60,
				"bucket", "previous-original", "previous-thumb", "previous-high", "previous-standard");
		ImageVersion current = new ImageVersion(image.id(), 2, "EDIT", currentStored);
		ImageVersion previous = new ImageVersion(image.id(), 1, "UPLOAD", previousStored);
		image.replaceCurrentVersion(current);
		when(images.selectById(image.id())).thenReturn(image);
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of());
		when(versions.selectById(current.id())).thenReturn(current);
		when(versions.selectByImageIdAndId(image.id(), previous.id())).thenReturn(previous);

		service.deleteVersion(image.id(), previous.id());

		verify(storage).delete(previousStored);
		verify(versions).deleteVersionById(previous.id());
		verify(auditLogService).record("image.version.delete", "IMAGE", image.id(),
				java.util.Map.of("versionId", previous.id(), "versionNo", 1));
	}

	@Test
	void cleanupExcessImageVersionsKeepsCurrentAndDeletesOldestNonCurrent() {
		ImageAsset image = image("image-version-retention", "版本保留", 0, 0);
		StoredImage v1Stored = new StoredImage("v1.jpg", "v1-sha", "image/jpeg", 101, 10, 10,
				"bucket", "v1-original", "v1-thumb", "v1-high", "v1-standard");
		StoredImage v2Stored = new StoredImage("v2.jpg", "v2-sha", "image/jpeg", 102, 20, 20,
				"bucket", "v2-original", "v2-thumb", "v2-high", "v2-standard");
		StoredImage v3Stored = new StoredImage("v3.jpg", "v3-sha", "image/jpeg", 103, 30, 30,
				"bucket", "v3-original", "v3-thumb", "v3-high", "v3-standard");
		ImageVersion v1 = new ImageVersion(image.id(), 1, "UPLOAD", v1Stored);
		ImageVersion v2 = new ImageVersion(image.id(), 2, "EDIT", v2Stored);
		ImageVersion v3 = new ImageVersion(image.id(), 3, "EDIT", v3Stored);
		image.replaceCurrentVersion(v1);
		when(settings.get("image.version.max_retained", "5")).thenReturn("2");
		when(versions.selectImageIdsExceedingRetainedLimit(2)).thenReturn(List.of(image.id()));
		when(versions.selectByImageIdOrdered(image.id())).thenReturn(List.of(v3, v2, v1));
		when(images.selectById(image.id())).thenReturn(image);

		int deleted = service.cleanupExcessImageVersions();

		assertThat(deleted).isEqualTo(1);
		verify(storage).delete(v2Stored);
		verify(versions).deleteVersionById(v2.id());
		verify(storage, never()).delete(v1Stored);
	}

	@Test
	void previewAppliesWatermarkWhenEnabled() {
		ImageAsset image = image("image-watermark", "水印图", 0, 0);
		StoredImage original = new StoredImage(image.originalFilename(), image.sha256(), image.mimeType(), image.sizeBytes(),
				image.width(), image.height(), "bucket", "original-key", "thumb-key", "high-key", "standard-key");
		ImageVersion current = new ImageVersion(image.id(), 1, "UPLOAD", original);
		byte[] source = new byte[] { 1, 2, 3 };
		byte[] watermarked = new byte[] { 4, 5, 6 };
		when(images.selectById(image.id())).thenReturn(image);
		when(imageTags.selectByImageIds(List.of(image.id()))).thenReturn(List.of());
		when(versions.findLatestByImageId(image.id())).thenReturn(Optional.of(current));
		when(settings.get("preview.quality", "ORIGINAL")).thenReturn("ORIGINAL");
		when(settings.get("watermark.enabled", "true")).thenReturn("true");
		when(settings.get("watermark.text", "仅供授权使用")).thenReturn("内部版权");
		when(storage.read("bucket", "original-key")).thenReturn(source);
		when(storage.watermark(source, "内部版权"))
				.thenReturn(new ImageStorageService.WatermarkedImage(watermarked, "image/png"));

		ImageService.ObjectFile file = service.preview(image.id());

		assertThat(file.mimeType()).isEqualTo("image/png");
		assertThat(file.filename()).isEqualTo("水印图-watermarked.png");
		assertThat(service.read(file)).containsExactly(watermarked);
	}

	@Test
	void purgeExpiredDeletedImagesDoesNothingWhenCleanupDisabled() {
		when(settings.get("soft_delete.cleanup.enabled", "false")).thenReturn("false");

		int purged = service.purgeExpiredDeletedImages();

		assertThat(purged).isZero();
		verify(images, never()).selectByStatusAndDeletedAtBefore(any(ImageStatus.class), any());
	}

	@Test
	void purgeExpiredDeletedImagesPurgesExpiredDeletedImagesWhenEnabled() {
		ImageAsset expired = image("image-expired", "到期停用", 0, 0);
		ReflectionTestUtils.setField(expired, "status", ImageStatus.DELETED);
		ReflectionTestUtils.setField(expired, "deletedAt", LocalDateTime.now().minusDays(8));
		when(settings.get("soft_delete.cleanup.enabled", "false")).thenReturn("true");
		when(settings.get("soft_delete.retention_days", "180")).thenReturn("7");
		when(images.selectByStatusAndDeletedAtBefore(any(ImageStatus.class), any())).thenReturn(List.of(expired));
		when(versions.selectByImageIdOrdered(expired.id())).thenReturn(List.of());

		int purged = service.purgeExpiredDeletedImages();

		assertThat(purged).isEqualTo(1);
		verify(imageTags).deleteByImageId(expired.id());
		verify(images).deleteById(expired.id());
		verify(auditLogService).record("image.purge.retention.cleanup", "IMAGE", "DELETED",
				java.util.Map.of("deleted", 1, "retentionDays", 7));
	}

	private ImageDailyCount dailyCount(LocalDate day, long total) {
		ImageDailyCount row = new ImageDailyCount();
		ReflectionTestUtils.setField(row, "day", day);
		ReflectionTestUtils.setField(row, "total", total);
		return row;
	}

	private ImageCategoryCount categoryCount(String categoryId, String name, long total) {
		ImageCategoryCount row = new ImageCategoryCount();
		ReflectionTestUtils.setField(row, "categoryId", categoryId);
		ReflectionTestUtils.setField(row, "name", name);
		ReflectionTestUtils.setField(row, "total", total);
		return row;
	}

	private ImageAsset image(String id, String title, long viewCount, long downloadCount) {
		ImageAsset image = new ImageAsset(id, title, title + ".jpg", id + "-sha", "image/jpeg", 1024, 100, 100);
		ReflectionTestUtils.setField(image, "viewCount", viewCount);
		ReflectionTestUtils.setField(image, "downloadCount", downloadCount);
		return image;
	}

	private Tag tag(String id, String groupId, String name, boolean enabled) {
		Tag tag = instantiate(Tag.class);
		ReflectionTestUtils.setField(tag, "id", id);
		ReflectionTestUtils.setField(tag, "groupId", groupId);
		ReflectionTestUtils.setField(tag, "name", name);
		ReflectionTestUtils.setField(tag, "enabled", enabled);
		return tag;
	}

	private TagGroup tagGroup(String id, String name, boolean enabled) {
		TagGroup group = instantiate(TagGroup.class);
		ReflectionTestUtils.setField(group, "id", id);
		ReflectionTestUtils.setField(group, "name", name);
		ReflectionTestUtils.setField(group, "enabled", enabled);
		return group;
	}

	private ImageTagLink imageTagLink(String imageId, String tagId) {
		ImageTagLink link = new ImageTagLink();
		ReflectionTestUtils.setField(link, "imageId", imageId);
		ReflectionTestUtils.setField(link, "tagId", tagId);
		return link;
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			var constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Cannot instantiate " + type.getSimpleName(), ex);
		}
	}
}
