package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.luna.wallpaper.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImageAssetMapperRuntimeTests {

	private static final String IMAGE_ID = "mapper-runtime-image";
	private static final String SECOND_IMAGE_ID = "mapper-runtime-image-second";
	private static final String SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	@Autowired
	private ImageAssetMapper images;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanImage() {
		jdbcTemplate.update("delete from images where id in (?, ?) or sha256 = ?", IMAGE_ID, SECOND_IMAGE_ID, SHA256);
	}

	@Test
	void schemaAllowsDuplicateSha256AndFindBySha256WorksThroughSpringMapperProxy() {
		insertImage(IMAGE_ID, "第一张重复图片", LocalDateTime.of(2024, 1, 1, 9, 0));
		insertImage(SECOND_IMAGE_ID, "第二张重复图片", LocalDateTime.of(2024, 1, 2, 9, 0));

		var existing = images.findBySha256AndStatusNot(SHA256, ImageStatus.DELETED);
		var duplicates = images.selectBySha256InAndStatusNot(List.of(SHA256), ImageStatus.DELETED);

		assertThat(existing).isPresent();
		assertThat(existing.get().id()).isEqualTo(IMAGE_ID);
		assertThat(duplicates).extracting(ImageAsset::id).containsExactly(IMAGE_ID, SECOND_IMAGE_ID);
	}

	private void insertImage(String id, String title, LocalDateTime createdAt) {
		jdbcTemplate.update("""
				insert into images (
				  id, title, title_sort_key, original_filename, sha256, mime_type,
				  size_bytes, width, height, status, view_count, download_count,
				  created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id, title, title, id + ".jpg", SHA256, "image/jpeg",
				1024L, 100, 80, "ACTIVE", 0L, 0L, createdAt, createdAt);
	}
}
