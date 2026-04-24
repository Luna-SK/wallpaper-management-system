package edu.wzut.wallpaper.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
