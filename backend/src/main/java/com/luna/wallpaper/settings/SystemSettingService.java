package com.luna.wallpaper.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingService {

	private final SystemSettingMapper mapper;

	SystemSettingService(SystemSettingMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public String get(String key, String defaultValue) {
		return java.util.Optional.ofNullable(mapper.selectById(key))
				.map(SystemSetting::getValue)
				.filter(value -> !value.isBlank())
				.orElse(defaultValue);
	}

	@Transactional
	public void put(String key, String value) {
		SystemSetting setting = mapper.selectById(key);
		if (setting == null) {
			mapper.insert(new SystemSetting(key, value));
			return;
		}
		setting.updateValue(value);
		mapper.updateById(setting);
	}
}
