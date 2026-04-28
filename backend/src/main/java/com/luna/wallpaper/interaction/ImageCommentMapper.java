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
			select count(*)
			from image_comments root
			where root.image_id = #{imageId}
			  and root.parent_comment_id is null
			  and (
			    root.status = 'ACTIVE'
			    or exists (
			      select 1
			      from image_comments child
			      where child.root_comment_id = root.id
			        and child.status = 'ACTIVE'
			    )
			  )
			""")
	long countVisibleRootThreadsByImageId(@Param("imageId") String imageId);

	@Select("""
			select comment.id,
			       comment.image_id imageId,
			       comment.user_id userId,
			       user.display_name authorName,
			       user.avatar_object_key authorAvatarObjectKey,
			       user.avatar_updated_at authorAvatarUpdatedAt,
			       comment.parent_comment_id parentCommentId,
			       comment.root_comment_id rootCommentId,
			       comment.depth,
			       comment.content,
			       comment.status,
			       exists (
			         select 1
			         from image_comments reply
			         where reply.parent_comment_id = comment.id
			       ) hasReplies,
			       comment.created_at createdAt,
			       comment.updated_at updatedAt
			from image_comments comment
			join app_users user on user.id = comment.user_id
			where comment.image_id = #{imageId}
			  and comment.parent_comment_id is null
			  and (
			    comment.status = 'ACTIVE'
			    or exists (
			      select 1
			      from image_comments child
			      where child.root_comment_id = comment.id
			        and child.status = 'ACTIVE'
			    )
			  )
			order by comment.created_at asc, comment.id asc
			limit #{size} offset #{offset}
			""")
	List<ImageCommentRow> selectVisibleRootRowsByImageId(@Param("imageId") String imageId,
			@Param("offset") long offset, @Param("size") long size);

	@Select("""
			<script>
			select comment.id,
			       comment.image_id imageId,
			       comment.user_id userId,
			       user.display_name authorName,
			       user.avatar_object_key authorAvatarObjectKey,
			       user.avatar_updated_at authorAvatarUpdatedAt,
			       comment.parent_comment_id parentCommentId,
			       comment.root_comment_id rootCommentId,
			       comment.depth,
			       comment.content,
			       comment.status,
			       exists (
			         select 1
			         from image_comments reply
			         where reply.parent_comment_id = comment.id
			       ) hasReplies,
			       comment.created_at createdAt,
			       comment.updated_at updatedAt
			from image_comments comment
			join app_users user on user.id = comment.user_id
			where comment.root_comment_id in
			<foreach collection="rootIds" item="rootId" open="(" separator="," close=")">
			  #{rootId}
			</foreach>
			order by comment.created_at asc, comment.id asc
			</script>
			""")
	List<ImageCommentRow> selectRowsByRootIds(@Param("rootIds") List<String> rootIds);

	@Select("""
			select id,
			       image_id,
			       user_id,
			       parent_comment_id,
			       root_comment_id,
			       depth,
			       content,
			       status,
			       created_at,
			       updated_at
			from image_comments
			where id = #{id}
			for update
			""")
	ImageComment selectByIdForUpdate(@Param("id") String id);

	@Select("""
			select count(*)
			from image_comments
			where parent_comment_id = #{parentId}
			""")
	long countByParentId(@Param("parentId") String parentId);

	@Delete("delete from image_comments where image_id = #{imageId}")
	int deleteByImageId(@Param("imageId") String imageId);

	@Delete("delete from image_comments where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);

	@Select("""
			select comment.id,
			       comment.image_id imageId,
			       comment.user_id userId,
			       user.display_name authorName,
			       user.avatar_object_key authorAvatarObjectKey,
			       user.avatar_updated_at authorAvatarUpdatedAt,
			       comment.parent_comment_id parentCommentId,
			       comment.root_comment_id rootCommentId,
			       comment.depth,
			       comment.content,
			       comment.status,
			       exists (
			         select 1
			         from image_comments reply
			         where reply.parent_comment_id = comment.id
			       ) hasReplies,
			       comment.created_at createdAt,
			       comment.updated_at updatedAt
			from image_comments comment
			join app_users user on user.id = comment.user_id
			where comment.id = #{id}
			""")
	ImageCommentRow selectRowById(@Param("id") String id);
}
