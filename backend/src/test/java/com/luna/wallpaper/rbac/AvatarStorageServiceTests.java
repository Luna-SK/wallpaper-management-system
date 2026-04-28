package com.luna.wallpaper.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.luna.wallpaper.config.StorageProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class AvatarStorageServiceTests {

	private final S3Client s3Client = mock(S3Client.class);
	private final StorageProperties storage = new StorageProperties("http://localhost:9000", "us-east-1",
			"access", "secret", "wallpaper-original", "wallpaper-preview", "wallpaper-thumbnail",
			"wallpaper-watermark", "wallpaper-audit");
	private final AvatarStorageService service = new AvatarStorageService(s3Client, storage);

	@Test
	void storeConvertsAvatarToSquarePng() throws Exception {
		when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());
		MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
				imageBytes("jpeg", 240, 160));
		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);

		AvatarStorageService.StoredAvatar avatar = service.store(file, "user-1");

		assertThat(avatar.objectKey()).startsWith("avatars/user-1/").endsWith(".png");
		assertThat(avatar.mimeType()).isEqualTo("image/png");
		verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
		assertThat(requestCaptor.getValue().bucket()).isEqualTo("wallpaper-original");
		assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
	}

	@Test
	void storeRejectsUnsupportedMimeType() {
		MockMultipartFile file = new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[] { 1, 2, 3 });

		assertThatThrownBy(() -> service.store(file, "user-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("头像仅支持 JPEG 或 PNG");
	}

	@Test
	void storeRejectsLargeFile() {
		MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
				new byte[2 * 1024 * 1024 + 1]);

		assertThatThrownBy(() -> service.store(file, "user-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("头像图片不能超过 2MB");
	}

	private static byte[] imageBytes(String format, int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}
}
