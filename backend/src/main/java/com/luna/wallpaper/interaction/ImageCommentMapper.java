package com.luna.wallpaper.interaction;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ImageCommentMapper extends BaseMapper<ImageComment> {

	@Select("""
			select count(*)
			from image_comments
			where image_id = #{imageId} and status = 'ACTIVE'
			""")
	long countActiveByImageId(@Param("imageId") String imageId);

	@Select("""
			select comment.id,
			       comment.image_id imageId,
			       comment.user_id userId,
			       user.display_name authorName,
			       comment.content,
			       comment.status,
			       comment.created_at createdAt,
			       comment.updated_at updatedAt
			from image_comments comment
			join app_users user on user.id = comment.user_id
			where comment.image_id = #{imageId} and comment.status = 'ACTIVE'
			order by comment.created_at asc, comment.id asc
			limit #{size} offset #{offset}
			""")
	List<ImageCommentRow> selectActiveRowsByImageId(@Param("imageId") String imageId,
			@Param("offset") long offset, @Param("size") long size);

	@Delete("delete from image_comments where image_id = #{imageId}")
	int deleteByImageId(@Param("imageId") String imageId);

	@Delete("delete from image_comments where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);

	@Select("""
			select comment.id,
			       comment.image_id imageId,
			       comment.user_id userId,
			       user.display_name authorName,
			       comment.content,
			       comment.status,
			       comment.created_at createdAt,
			       comment.updated_at updatedAt
			from image_comments comment
			join app_users user on user.id = comment.user_id
			where comment.id = #{id}
			""")
	ImageCommentRow selectRowById(@Param("id") String id);
}
