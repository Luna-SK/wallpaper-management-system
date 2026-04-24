package com.luna.wallpaper.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
