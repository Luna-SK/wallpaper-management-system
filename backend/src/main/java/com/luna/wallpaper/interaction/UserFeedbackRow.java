package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;

public class UserFeedbackRow {
	private String id;
	private String userId;
	private String username;
	private String displayName;
	private String imageId;
	private String imageTitle;
	private String type;
	private String title;
	private String content;
	private String status;
	private String response;
	private String handledBy;
	private LocalDateTime handledAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	String id() { return id; }
	String userId() { return userId; }
	String username() { return username; }
	String displayName() { return displayName; }
	String imageId() { return imageId; }
	String imageTitle() { return imageTitle; }
	String type() { return type; }
	String title() { return title; }
	String content() { return content; }
	String status() { return status; }
	String response() { return response; }
	String handledBy() { return handledBy; }
	LocalDateTime handledAt() { return handledAt; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime updatedAt() { return updatedAt; }
}
