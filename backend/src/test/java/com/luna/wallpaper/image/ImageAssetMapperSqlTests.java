package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ImageAssetMapperSqlTests {

	@Test
	void keywordSearchOnlyMatchesImageTitle() throws NoSuchMethodException {
		assertTitleOnlyKeywordSearch(sql("countSearch", String.class, String.class, String.class,
				ImageStatus.class, boolean.class, String.class));
		assertTitleOnlyKeywordSearch(sql("searchIds", String.class, String.class, String.class,
				ImageStatus.class, boolean.class, String.class, String.class, String.class,
				long.class, long.class));
	}

	private static void assertTitleOnlyKeywordSearch(String sql) {
		assertThat(sql)
				.contains("lower(image.title) like lower(concat('%', #{keyword}, '%'))")
				.doesNotContain("original_filename")
				.doesNotContain("lower(tag.name) like");
	}

	private static String sql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Select select = ImageAssetMapper.class.getMethod(methodName, parameterTypes).getAnnotation(Select.class);
		return String.join("\n", select.value());
	}
}
