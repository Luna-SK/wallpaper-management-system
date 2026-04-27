package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("image_favorites")
public class ImageFavorite {

	@TableId(type = IdType.INPUT)
	private String id;

	private String imageId;

	private String userId;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	protected ImageFavorite() {
	}

	ImageFavorite(String imageId, String userId) {
		this.id = UUID.randomUUID().toString();
		this.imageId = imageId;
		this.userId = userId;
	}

	String id() { return id; }
	String imageId() { return imageId; }
	String userId() { return userId; }
}
