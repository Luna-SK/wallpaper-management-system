package com.luna.wallpaper.settings;

public final class WatermarkSettings {
	public static final String ENABLED = "watermark.enabled";
	public static final String PREVIEW_ENABLED = "watermark.preview.enabled";
	public static final String TEXT = "watermark.text";
	public static final String MODE = "watermark.mode";
	public static final String POSITION = "watermark.position";
	public static final String OPACITY_PERCENT = "watermark.opacity_percent";
	public static final String TILE_DENSITY = "watermark.tile_density";
	public static final String DEFAULT_TEXT = "仅供授权使用";
	public static final String DEFAULT_MODE = "CORNER";
	public static final String DEFAULT_POSITION = "BOTTOM_RIGHT";
	public static final String DEFAULT_TILE_DENSITY = "SPARSE";
	public static final int DEFAULT_OPACITY_PERCENT = 16;
	public static final int MIN_OPACITY_PERCENT = 5;
	public static final int MAX_OPACITY_PERCENT = 40;
	public static final int MAX_TEXT_LENGTH = 64;

	private WatermarkSettings() {
	}
}
