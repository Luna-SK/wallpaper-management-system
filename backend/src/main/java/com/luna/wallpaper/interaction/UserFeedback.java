package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_feedback")
public class UserFeedback {

	@TableId(type = IdType.INPUT)
	private String id;

	private String userId;

	private String imageId;

	private String type;

	private String title;

	private String content;

	private FeedbackStatus status = FeedbackStatus.OPEN;

	private String response;

	private String handledBy;

	private LocalDateTime handledAt;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected UserFeedback() {
	}

	UserFeedback(String userId, String imageId, String type, String title, String content) {
		this.id = UUID.randomUUID().toString();
		this.userId = userId;
		this.imageId = imageId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.status = FeedbackStatus.OPEN;
	}

	String id() { return id; }
	String userId() { return userId; }
	FeedbackStatus status() { return status; }

	void handle(FeedbackStatus status, String response, String handlerId) {
		this.status = status;
		this.response = response;
		this.handledBy = handlerId;
		this.handledAt = LocalDateTime.now();
	}

	void close() {
		this.status = FeedbackStatus.CLOSED;
	}
}
