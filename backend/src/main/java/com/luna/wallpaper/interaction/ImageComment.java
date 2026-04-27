package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("image_comments")
public class ImageComment {

	@TableId(type = IdType.INPUT)
	private String id;

	private String imageId;

	private String userId;

	private String content;

	private ImageCommentStatus status = ImageCommentStatus.ACTIVE;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected ImageComment() {
	}

	ImageComment(String imageId, String userId, String content) {
		this.id = UUID.randomUUID().toString();
		this.imageId = imageId;
		this.userId = userId;
		this.content = content;
		this.status = ImageCommentStatus.ACTIVE;
	}

	String id() { return id; }
	String imageId() { return imageId; }
	String userId() { return userId; }
	String content() { return content; }
	ImageCommentStatus status() { return status; }

	void updateContent(String content) {
		this.content = content;
	}

	void delete() {
		this.status = ImageCommentStatus.DELETED;
	}
}
