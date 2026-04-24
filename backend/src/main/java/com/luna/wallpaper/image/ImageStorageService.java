package com.luna.wallpaper.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
class ImageStorageService {

	private final S3Client s3Client;
	private final StorageProperties storage;

	ImageStorageService(S3Client s3Client, StorageProperties storage) {
		this.s3Client = s3Client;
		this.storage = storage;
	}

	StoredImage store(MultipartFile file, String imageId) {
		String bucket = storage.bucketOriginal();
		List<String> writtenKeys = new ArrayList<>();
		try {
			String mimeType = normalizeMime(file);
			byte[] originalBytes = file.getBytes();
			String sha256 = sha256(originalBytes);
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(originalBytes));
			Integer width = source == null ? null : source.getWidth();
			Integer height = source == null ? null : source.getHeight();
			byte[] thumbnail = source == null ? originalBytes : resizePng(source, 320);
			byte[] highPreview = source == null ? originalBytes : resizePng(source, 1600);
			byte[] standardPreview = source == null ? originalBytes : resizePng(source, 960);

			String baseKey = "images/" + imageId + "/" + sha256;
			String originalKey = baseKey + "/original/" + safeFilename(file.getOriginalFilename());
			String thumbnailKey = baseKey + "/thumbnail.png";
			String highKey = baseKey + "/preview-high.png";
			String standardKey = baseKey + "/preview-standard.png";
			ensureBucket(bucket);
			put(bucket, originalKey, mimeType, originalBytes);
			writtenKeys.add(originalKey);
			put(bucket, thumbnailKey, "image/png", thumbnail);
			writtenKeys.add(thumbnailKey);
			put(bucket, highKey, "image/png", highPreview);
			writtenKeys.add(highKey);
			put(bucket, standardKey, "image/png", standardPreview);
			writtenKeys.add(standardKey);
			return new StoredImage(file.getOriginalFilename(), sha256, mimeType, originalBytes.length, width, height, bucket,
					originalKey, thumbnailKey, highKey, standardKey);
		}
		catch (IOException ex) {
			deleteObjectsQuietly(bucket, writtenKeys);
			throw new IllegalArgumentException("读取图片文件失败");
		}
		catch (RuntimeException ex) {
			deleteObjectsQuietly(bucket, writtenKeys);
			throw ex;
		}
	}

	byte[] read(String bucket, String objectKey) {
		ResponseBytes<?> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
		return bytes.asByteArray();
	}

	void delete(StoredImage stored) {
		if (stored == null || stored.bucket() == null) {
			return;
		}
		deleteObjects(stored.bucket(), List.of(stored.originalObjectKey(), stored.thumbnailObjectKey(),
				stored.highPreviewObjectKey(), stored.standardPreviewObjectKey()));
	}

	void deleteQuietly(StoredImage stored) {
		if (stored == null || stored.bucket() == null) {
			return;
		}
		deleteObjectsQuietly(stored.bucket(), List.of(stored.originalObjectKey(), stored.thumbnailObjectKey(),
				stored.highPreviewObjectKey(), stored.standardPreviewObjectKey()));
	}

	void deleteObjectsQuietly(String bucket, Collection<String> keys) {
		try {
			deleteObjects(bucket, keys);
		}
		catch (RuntimeException ignored) {
		}
	}

	List<StoredObject> listImageObjects() {
		String bucket = storage.bucketOriginal();
		try {
			ensureBucket(bucket);
			List<StoredObject> objects = new ArrayList<>();
			String continuationToken = null;
			do {
				var response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
						.bucket(bucket)
						.prefix("images/")
						.continuationToken(continuationToken)
						.build());
				objects.addAll(response.contents().stream()
						.map(object -> new StoredObject(bucket, object.key(), object.lastModified()))
						.toList());
				continuationToken = response.nextContinuationToken();
			}
			while (continuationToken != null);
			return objects;
		}
		catch (NoSuchBucketException ex) {
			return List.of();
		}
	}

	private void put(String bucket, String key, String mimeType, byte[] bytes) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
				RequestBody.fromBytes(bytes));
	}

	private void deleteObjects(String bucket, Collection<String> keys) {
		if (bucket == null || keys == null || keys.isEmpty()) {
			return;
		}
		for (String key : keys) {
			if (key == null || key.isBlank()) {
				continue;
			}
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		}
	}

	private void ensureBucket(String bucket) {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		}
		catch (NoSuchBucketException ex) {
			s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
				return;
			}
			throw ex;
		}
	}

	private static byte[] resizePng(BufferedImage source, int maxSide) throws IOException {
		double ratio = Math.min(1D, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
		int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
		BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = resized.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(source, 0, 0, width, height, null);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(resized, "png", output);
		return output.toByteArray();
	}

	private static String normalizeMime(MultipartFile file) {
		String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
		if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp")) {
			throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片");
		}
		return mime;
	}

	private static String safeFilename(String filename) {
		String value = filename == null || filename.isBlank() ? "image" : filename;
		return value.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 不可用", ex);
		}
	}

	record StoredObject(String bucket, String key, Instant lastModified) {
	}
}
