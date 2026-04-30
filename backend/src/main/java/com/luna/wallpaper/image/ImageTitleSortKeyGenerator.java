package com.luna.wallpaper.image;

import java.text.Normalizer;
import java.util.Locale;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

final class ImageTitleSortKeyGenerator {

	private static final int MAX_SORT_KEY_LENGTH = 512;
	private static final HanyuPinyinOutputFormat PINYIN_FORMAT = pinyinFormat();

	private ImageTitleSortKeyGenerator() {
	}

	static String generate(String title) {
		if (title == null || title.isBlank()) {
			return "";
		}
		String normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder key = new StringBuilder(normalized.length() * 2);
		normalized.codePoints().forEach(codePoint -> appendSortToken(key, codePoint));
		return key.length() <= MAX_SORT_KEY_LENGTH ? key.toString() : key.substring(0, MAX_SORT_KEY_LENGTH);
	}

	private static void appendSortToken(StringBuilder key, int codePoint) {
		if (Character.charCount(codePoint) == 1) {
			String[] pinyin = pinyinFor((char) codePoint);
			if (pinyin != null && pinyin.length > 0) {
				key.append(pinyin[0]);
				return;
			}
		}
		key.appendCodePoint(codePoint);
	}

	private static String[] pinyinFor(char character) {
		try {
			return PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
		}
		catch (BadHanyuPinyinOutputFormatCombination ex) {
			throw new IllegalStateException("Invalid pinyin output format", ex);
		}
	}

	private static HanyuPinyinOutputFormat pinyinFormat() {
		HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
		format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
		format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
		format.setVCharType(HanyuPinyinVCharType.WITH_V);
		return format;
	}
}
