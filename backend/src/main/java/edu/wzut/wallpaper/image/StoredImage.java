package edu.wzut.wallpaper.image;

record StoredImage(String originalFilename, String sha256, String mimeType, long sizeBytes, Integer width, Integer height,
		String bucket, String originalObjectKey, String thumbnailObjectKey, String highPreviewObjectKey,
		String standardPreviewObjectKey) {
}
