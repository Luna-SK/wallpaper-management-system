package com.luna.wallpaper.settings;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("system_settings")
class SystemSetting {

	@TableId(value = "setting_key", type = IdType.INPUT)
	private String settingKey;

	@TableField("setting_value")
	private String value;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected SystemSetting() {
	}

	SystemSetting(String key, String value) {
		this.settingKey = key;
		this.value = value;
		this.updatedAt = LocalDateTime.now();
	}

	String getKey() {
		return settingKey;
	}

	String getValue() {
		return value;
	}

	void updateValue(String value) {
		this.value = value;
		this.updatedAt = LocalDateTime.now();
	}
}
