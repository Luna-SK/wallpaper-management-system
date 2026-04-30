package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageAssetTests {

	@Test
	void constructorInitializesTitleSortKeyForUploads() {
		ImageAsset image = new ImageAsset("image-1", "吊经001", "diao.jpg", "sha", "image/jpeg", 1024, 100, 100);

		assertThat(image.titleSortKey()).isEqualTo("diaojing001");
	}

	@Test
	void updateMetadataRefreshesTitleSortKey() {
		ImageAsset image = new ImageAsset("image-1", "三丝001", "san.jpg", "sha", "image/jpeg", 1024, 100, 100);

		image.updateMetadata("破洞001", ImageStatus.ACTIVE);

		assertThat(image.titleSortKey()).isEqualTo("podong001");
	}
}
