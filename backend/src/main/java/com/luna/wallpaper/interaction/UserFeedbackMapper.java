package com.luna.wallpaper.interaction;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {

	@Select("""
			<script>
			select count(*)
			from user_feedback feedback
			where feedback.user_id = #{userId}
			  <if test="status != null">and feedback.status = #{status}</if>
			</script>
			""")
	long countMine(@Param("userId") String userId, @Param("status") FeedbackStatus status);

	@Select("""
			<script>
			select feedback.id,
			       feedback.user_id userId,
			       user.username,
			       user.display_name displayName,
			       feedback.image_id imageId,
			       image.title imageTitle,
			       feedback.type,
			       feedback.title,
			       feedback.content,
			       feedback.status,
			       feedback.response,
			       feedback.handled_by handledBy,
			       feedback.handled_at handledAt,
			       feedback.created_at createdAt,
			       feedback.updated_at updatedAt
			from user_feedback feedback
			join app_users user on user.id = feedback.user_id
			left join images image on image.id = feedback.image_id
			where feedback.user_id = #{userId}
			  <if test="status != null">and feedback.status = #{status}</if>
			order by feedback.created_at desc, feedback.id desc
			limit #{size} offset #{offset}
			</script>
			""")
	List<UserFeedbackRow> selectMine(@Param("userId") String userId, @Param("status") FeedbackStatus status,
			@Param("offset") long offset, @Param("size") long size);

	@Select("""
			<script>
			select count(*)
			from user_feedback feedback
			join app_users user on user.id = feedback.user_id
			where 1 = 1
			  <if test="status != null">and feedback.status = #{status}</if>
			  <if test="keyword != null">
			    and (lower(feedback.title) like lower(concat('%', #{keyword}, '%'))
			      or lower(feedback.content) like lower(concat('%', #{keyword}, '%'))
			      or lower(user.username) like lower(concat('%', #{keyword}, '%'))
			      or lower(user.display_name) like lower(concat('%', #{keyword}, '%')))
			  </if>
			</script>
			""")
	long countAdmin(@Param("keyword") String keyword, @Param("status") FeedbackStatus status);

	@Select("""
			<script>
			select feedback.id,
			       feedback.user_id userId,
			       user.username,
			       user.display_name displayName,
			       feedback.image_id imageId,
			       image.title imageTitle,
			       feedback.type,
			       feedback.title,
			       feedback.content,
			       feedback.status,
			       feedback.response,
			       feedback.handled_by handledBy,
			       feedback.handled_at handledAt,
			       feedback.created_at createdAt,
			       feedback.updated_at updatedAt
			from user_feedback feedback
			join app_users user on user.id = feedback.user_id
			left join images image on image.id = feedback.image_id
			where 1 = 1
			  <if test="status != null">and feedback.status = #{status}</if>
			  <if test="keyword != null">
			    and (lower(feedback.title) like lower(concat('%', #{keyword}, '%'))
			      or lower(feedback.content) like lower(concat('%', #{keyword}, '%'))
			      or lower(user.username) like lower(concat('%', #{keyword}, '%'))
			      or lower(user.display_name) like lower(concat('%', #{keyword}, '%')))
			  </if>
			order by feedback.created_at desc, feedback.id desc
			limit #{size} offset #{offset}
			</script>
			""")
	List<UserFeedbackRow> selectAdmin(@Param("keyword") String keyword, @Param("status") FeedbackStatus status,
			@Param("offset") long offset, @Param("size") long size);

	@Update("update user_feedback set image_id = null, updated_at = now(6) where image_id = #{imageId}")
	int clearImageId(@Param("imageId") String imageId);

	@Delete("delete from user_feedback where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);

	@Select("""
			select feedback.id,
			       feedback.user_id userId,
			       user.username,
			       user.display_name displayName,
			       feedback.image_id imageId,
			       image.title imageTitle,
			       feedback.type,
			       feedback.title,
			       feedback.content,
			       feedback.status,
			       feedback.response,
			       feedback.handled_by handledBy,
			       feedback.handled_at handledAt,
			       feedback.created_at createdAt,
			       feedback.updated_at updatedAt
			from user_feedback feedback
			join app_users user on user.id = feedback.user_id
			left join images image on image.id = feedback.image_id
			where feedback.id = #{id}
			""")
	UserFeedbackRow selectRowById(@Param("id") String id);
}
