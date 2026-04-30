package com.luna.wallpaper.image;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
class ImageTitleSortKeyBackfillService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImageTitleSortKeyBackfillService.class);
	private static final int BATCH_SIZE = 500;

	private final ImageAssetMapper images;

	ImageTitleSortKeyBackfillService(ImageAssetMapper images) {
		this.images = images;
	}

	@Override
	public void run(ApplicationArguments args) {
		int updated = 0;
		while (true) {
			List<ImageAsset> batch = images.selectMissingTitleSortKey(BATCH_SIZE);
			if (batch.isEmpty()) {
				break;
			}
			for (ImageAsset image : batch) {
				image.refreshTitleSortKey();
				updated += images.updateTitleSortKey(image.id(), image.titleSortKey());
			}
		}
		if (updated > 0) {
			log.info("Backfilled title sort keys for {} images", updated);
		}
	}
}
