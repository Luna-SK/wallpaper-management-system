package edu.wzut.wallpaper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WallpaperBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WallpaperBackendApplication.class, args);
	}

}
