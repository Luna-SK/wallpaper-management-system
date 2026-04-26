package com.luna.wallpaper.taxonomy;

import com.luna.wallpaper.taxonomy.TaxonomyDtos.ReferenceImpact;

public class TaxonomyReferenceException extends RuntimeException {

	private final ReferenceImpact impact;

	TaxonomyReferenceException(String message, ReferenceImpact impact) {
		super(message);
		this.impact = impact;
	}

	public ReferenceImpact impact() {
		return impact;
	}
}
