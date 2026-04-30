package com.luna.wallpaper.image;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ImageTitleSortKeyBackfillServiceTests {

	private final ImageAssetMapper images = mock(ImageAssetMapper.class);
	private final ImageTitleSortKeyBackfillService service = new ImageTitleSortKeyBackfillService(images);

	@Test
	void backfillsMissingTitleSortKeysUntilNoRowsRemain() {
		ImageAsset diaojing = new ImageAsset("image-1", "吊经001", "diao.jpg", "sha-1", "image/jpeg", 1024, 100, 100);
		ImageAsset sansi = new ImageAsset("image-2", "三丝001", "san.jpg", "sha-2", "image/jpeg", 1024, 100, 100);
		AtomicInteger calls = new AtomicInteger();
		when(images.selectMissingTitleSortKey(500)).thenAnswer(invocation ->
				calls.getAndIncrement() == 0 ? List.of(diaojing, sansi) : List.of());
		when(images.updateTitleSortKey("image-1", "diaojing001")).thenReturn(1);
		when(images.updateTitleSortKey("image-2", "sansi001")).thenReturn(1);

		service.run(null);

		verify(images).updateTitleSortKey("image-1", "diaojing001");
		verify(images).updateTitleSortKey("image-2", "sansi001");
	}

	@Test
	void doesNothingWhenNoRowsNeedBackfill() {
		when(images.selectMissingTitleSortKey(500)).thenReturn(List.of());

		service.run(null);

		verify(images, never()).updateTitleSortKey("image-1", "diaojing001");
	}
}
