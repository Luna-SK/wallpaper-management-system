package com.luna.wallpaper.interaction;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ImageLikeMapper extends BaseMapper<ImageLike> {

	@Insert("""
			insert ignore into image_likes (id, image_id, user_id, created_at)
			values (#{id}, #{imageId}, #{userId}, now(6))
			""")
	int insertIgnore(@Param("id") String id, @Param("imageId") String imageId, @Param("userId") String userId);

	@Delete("delete from image_likes where image_id = #{imageId} and user_id = #{userId}")
	int deleteByImageIdAndUserId(@Param("imageId") String imageId, @Param("userId") String userId);

	@Delete("delete from image_likes where image_id = #{imageId}")
	int deleteByImageId(@Param("imageId") String imageId);

	@Delete("delete from image_likes where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);
}
