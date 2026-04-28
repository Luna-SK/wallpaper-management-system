package com.luna.wallpaper.rbac;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.luna.wallpaper.config.StorageProperties;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
class AvatarStorageService {

	static final int AVATAR_SIZE = 160;
	private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
	private static final String AVATAR_MIME_TYPE = "image/png";

	private final S3Client s3Client;
	private final StorageProperties storage;

	AvatarStorageService(S3Client s3Client, StorageProperties storage) {
		this.s3Client = s3Client;
		this.storage = storage;
	}

	StoredAvatar store(MultipartFile file, String userId) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择头像图片");
		}
		if (file.getSize() > MAX_AVATAR_BYTES) {
			throw new IllegalArgumentException("头像图片不能超过 2MB");
		}
		String mimeType = normalizeMime(file.getContentType());
		if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
			throw new IllegalArgumentException("头像仅支持 JPEG 或 PNG");
		}
		try {
			byte[] bytes = file.getBytes();
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
			if (source == null) {
				throw new IllegalArgumentException("头像图片无法识别");
			}
			byte[] avatarBytes = squareAvatar(source);
			String objectKey = "avatars/" + userId + "/" + UUID.randomUUID() + ".png";
			ensureBucket(storage.bucketOriginal());
			s3Client.putObject(PutObjectRequest.builder()
							.bucket(storage.bucketOriginal())
							.key(objectKey)
							.contentType(AVATAR_MIME_TYPE)
							.build(),
					RequestBody.fromBytes(avatarBytes));
			return new StoredAvatar(objectKey, AVATAR_MIME_TYPE);
		} catch (IOException ex) {
			throw new IllegalArgumentException("读取头像图片失败");
		}
	}

	AvatarFile read(AppUser user) {
		if (user == null || user.avatarObjectKey() == null || user.avatarObjectKey().isBlank()) {
			throw new IllegalArgumentException("用户头像不存在");
		}
		ResponseBytes<?> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(storage.bucketOriginal())
				.key(user.avatarObjectKey())
				.build());
		return new AvatarFile(bytes.asByteArray(), user.avatarMimeType() == null ? AVATAR_MIME_TYPE : user.avatarMimeType(),
				Instant.now());
	}

	void deleteQuietly(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder()
					.bucket(storage.bucketOriginal())
					.key(objectKey)
					.build());
		} catch (RuntimeException ignored) {
		}
	}

	private void ensureBucket(String bucket) {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		} catch (NoSuchBucketException ex) {
			s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		} catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
				return;
			}
			throw ex;
		}
	}

	private static String normalizeMime(String mimeType) {
		if (mimeType == null) {
			return "";
		}
		String normalized = mimeType.toLowerCase().trim();
		return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
	}

	private static byte[] squareAvatar(BufferedImage source) throws IOException {
		int side = Math.min(source.getWidth(), source.getHeight());
		int x = Math.max(0, (source.getWidth() - side) / 2);
		int y = Math.max(0, (source.getHeight() - side) / 2);
		BufferedImage canvas = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(source, 0, 0, AVATAR_SIZE, AVATAR_SIZE, x, y, x + side, y + side, null);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(canvas, "png", output);
		return output.toByteArray();
	}

	record StoredAvatar(String objectKey, String mimeType) {
	}

	record AvatarFile(byte[] content, String mimeType, Instant lastModified) {
	}
}
