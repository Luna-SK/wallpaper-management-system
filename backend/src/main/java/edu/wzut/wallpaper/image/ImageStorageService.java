package edu.wzut.wallpaper.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import edu.wzut.wallpaper.config.StorageProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
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
			String bucket = storage.bucketOriginal();
			ensureBucket(bucket);
			put(bucket, originalKey, mimeType, originalBytes);
			put(bucket, thumbnailKey, "image/png", thumbnail);
			put(bucket, highKey, "image/png", highPreview);
			put(bucket, standardKey, "image/png", standardPreview);
			return new StoredImage(file.getOriginalFilename(), sha256, mimeType, originalBytes.length, width, height, bucket,
					originalKey, thumbnailKey, highKey, standardKey);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("读取图片文件失败");
		}
	}

	byte[] read(String bucket, String objectKey) {
		ResponseBytes<?> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
		return bytes.asByteArray();
	}

	private void put(String bucket, String key, String mimeType, byte[] bytes) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
				RequestBody.fromBytes(bytes));
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
}
