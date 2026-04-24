package com.luna.wallpaper.settings;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings")
class SystemSetting {

	@Id
	@Column(name = "setting_key", nullable = false, length = 120)
	private String key;

	@Column(name = "setting_value", columnDefinition = "text")
	private String value;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected SystemSetting() {
	}

	SystemSetting(String key, String value) {
		this.key = key;
		this.value = value;
		this.updatedAt = LocalDateTime.now();
	}

	String getKey() {
		return key;
	}

	String getValue() {
		return value;
	}

	void updateValue(String value) {
		this.value = value;
		this.updatedAt = LocalDateTime.now();
	}
}
