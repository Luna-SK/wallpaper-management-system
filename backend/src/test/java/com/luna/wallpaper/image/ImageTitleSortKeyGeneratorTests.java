package com.luna.wallpaper.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImageTitleSortKeyGeneratorTests {

	@Test
	void generatesPlainLowercasePinyinSortKey() {
		assertThat(ImageTitleSortKeyGenerator.generate("吊经")).isEqualTo("diaojing");
		assertThat(ImageTitleSortKeyGenerator.generate("三丝")).isEqualTo("sansi");
		assertThat(ImageTitleSortKeyGenerator.generate("破洞")).isEqualTo("podong");
	}

	@Test
	void preservesNonChineseSeparatorsAndDigits() {
		assertThat(ImageTitleSortKeyGenerator.generate("水渍-油渍-污渍001"))
				.isEqualTo("shuizi-youzi-wuzi001");
	}

	@Test
	void pinyinKeysSortChineseTitlesByExpectedOrder() {
		List<String> titles = List.of("三丝", "吊经", "破洞", "水渍");

		List<String> sorted = titles.stream()
				.sorted(Comparator.comparing(ImageTitleSortKeyGenerator::generate))
				.toList();

		assertThat(sorted).containsExactly("吊经", "破洞", "三丝", "水渍");
	}

	@Test
	void truncatesSortKeyToDatabaseColumnLength() {
		String key = ImageTitleSortKeyGenerator.generate("墙".repeat(300));

		assertThat(key).hasSize(512);
	}
}
