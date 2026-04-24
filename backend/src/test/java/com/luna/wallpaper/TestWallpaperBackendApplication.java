package com.luna.wallpaper;

import org.springframework.boot.SpringApplication;

public class TestWallpaperBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(WallpaperBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
