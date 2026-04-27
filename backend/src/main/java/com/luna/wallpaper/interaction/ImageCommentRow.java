package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;

public class ImageCommentRow {
	private String id;
	private String imageId;
	private String userId;
	private String authorName;
	private String content;
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	String id() { return id; }
	String imageId() { return imageId; }
	String userId() { return userId; }
	String authorName() { return authorName; }
	String content() { return content; }
	String status() { return status; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime updatedAt() { return updatedAt; }
}
