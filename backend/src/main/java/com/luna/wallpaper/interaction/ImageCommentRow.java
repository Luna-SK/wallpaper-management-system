package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;

public class ImageCommentRow {
	private String id;
	private String imageId;
	private String userId;
	private String authorName;
	private String authorAvatarObjectKey;
	private LocalDateTime authorAvatarUpdatedAt;
	private String parentCommentId;
	private String rootCommentId;
	private Integer depth;
	private String content;
	private String status;
	private Boolean hasReplies;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	String id() { return id; }
	String imageId() { return imageId; }
	String userId() { return userId; }
	String authorName() { return authorName; }
	String authorAvatarObjectKey() { return authorAvatarObjectKey; }
	LocalDateTime authorAvatarUpdatedAt() { return authorAvatarUpdatedAt; }
	String parentCommentId() { return parentCommentId; }
	String rootCommentId() { return rootCommentId; }
	int depth() { return depth == null ? 0 : depth; }
	String content() { return content; }
	String status() { return status; }
	boolean hasReplies() { return Boolean.TRUE.equals(hasReplies); }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime updatedAt() { return updatedAt; }
}
