package com.luna.wallpaper.image;

import java.util.Arrays;

record ImageSort(ImageSortField field, ImageSortDirection direction) {

	static ImageSort defaultSort() {
		return new ImageSort(ImageSortField.CREATED_AT, ImageSortDirection.DESC);
	}

	static ImageSort parse(String sortBy, String sortDirection) {
		ImageSortField field = ImageSortField.parseOrNull(sortBy);
		return field == null ? defaultSort() : new ImageSort(field, ImageSortDirection.parse(sortDirection));
	}

	String fieldName() {
		return field.name();
	}

	String directionSql() {
		return direction.sql();
	}
}

enum ImageSortField {
	CREATED_AT("createdAt"),
	UPDATED_AT("updatedAt"),
	TITLE("title"),
	SIZE_BYTES("sizeBytes"),
	RESOLUTION("resolution"),
	COMMENT_COUNT("commentCount"),
	FAVORITE_COUNT("favoriteCount"),
	LIKE_COUNT("likeCount"),
	VIEW_COUNT("viewCount"),
	DOWNLOAD_COUNT("downloadCount");

	private final String apiName;

	ImageSortField(String apiName) {
		this.apiName = apiName;
	}

	static ImageSortField parseOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = normalize(value);
		return Arrays.stream(values())
				.filter(field -> normalize(field.apiName).equals(normalized) || normalize(field.name()).equals(normalized))
				.findFirst()
				.orElse(null);
	}

	private static String normalize(String value) {
		return value.replace("_", "").replace("-", "").toLowerCase();
	}
}

enum ImageSortDirection {
	ASC("asc"),
	DESC("desc");

	private final String sql;

	ImageSortDirection(String sql) {
		this.sql = sql;
	}

	static ImageSortDirection parse(String value) {
		if (value == null || value.isBlank()) {
			return DESC;
		}
		return "asc".equalsIgnoreCase(value.trim()) ? ASC : DESC;
	}

	String sql() {
		return sql;
	}
}
