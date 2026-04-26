package com.luna.wallpaper.rbac;

import com.luna.wallpaper.rbac.RbacDtos.RbacReferenceImpact;

public class RbacReferenceException extends RuntimeException {
	private final RbacReferenceImpact impact;

	RbacReferenceException(String message, RbacReferenceImpact impact) {
		super(message);
		this.impact = impact;
	}

	public RbacReferenceImpact impact() {
		return impact;
	}
}
