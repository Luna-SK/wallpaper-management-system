package edu.wzut.wallpaper.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingService {

	private final SystemSettingRepository repository;

	SystemSettingService(SystemSettingRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public String get(String key, String defaultValue) {
		return repository.findById(key)
				.map(SystemSetting::getValue)
				.filter(value -> !value.isBlank())
				.orElse(defaultValue);
	}

	@Transactional
	public void put(String key, String value) {
		SystemSetting setting = repository.findById(key)
				.orElseGet(() -> new SystemSetting(key, value));
		setting.updateValue(value);
		repository.save(setting);
	}
}
