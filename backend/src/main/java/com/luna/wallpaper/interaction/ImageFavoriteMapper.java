package com.luna.wallpaper.interaction;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ImageFavoriteMapper extends BaseMapper<ImageFavorite> {

	@Insert("""
			insert ignore into image_favorites (id, image_id, user_id, created_at)
			values (#{id}, #{imageId}, #{userId}, now(6))
			""")
	int insertIgnore(@Param("id") String id, @Param("imageId") String imageId, @Param("userId") String userId);

	@Delete("delete from image_favorites where image_id = #{imageId} and user_id = #{userId}")
	int deleteByImageIdAndUserId(@Param("imageId") String imageId, @Param("userId") String userId);

	@Delete("delete from image_favorites where image_id = #{imageId}")
	int deleteByImageId(@Param("imageId") String imageId);

	@Delete("delete from image_favorites where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);

	@Select("""
			<script>
			select base.imageId imageId,
			       coalesce(favorite_stats.favoriteCount, 0) favoriteCount,
			       coalesce(like_stats.likeCount, 0) likeCount,
			       coalesce(comment_stats.commentCount, 0) commentCount,
			       case when my_favorite.image_id is null then false else true end favoritedByMe,
			       case when my_like.image_id is null then false else true end likedByMe
			from (
			  <foreach collection="ids" item="id" separator=" union all ">
			    select #{id} imageId
			  </foreach>
			) base
			left join (
			  select image_id, count(*) favoriteCount
			  from image_favorites
			  where image_id in
			  <foreach collection="ids" item="id" open="(" separator="," close=")">
			    #{id}
			  </foreach>
			  group by image_id
			) favorite_stats on favorite_stats.image_id = base.imageId
			left join (
			  select image_id, count(*) likeCount
			  from image_likes
			  where image_id in
			  <foreach collection="ids" item="id" open="(" separator="," close=")">
			    #{id}
			  </foreach>
			  group by image_id
			) like_stats on like_stats.image_id = base.imageId
			left join (
			  select image_id, count(*) commentCount
			  from image_comments
			  where status = 'ACTIVE' and image_id in
			  <foreach collection="ids" item="id" open="(" separator="," close=")">
			    #{id}
			  </foreach>
			  group by image_id
			) comment_stats on comment_stats.image_id = base.imageId
			left join image_favorites my_favorite on my_favorite.image_id = base.imageId and my_favorite.user_id = #{userId}
			left join image_likes my_like on my_like.image_id = base.imageId and my_like.user_id = #{userId}
			</script>
			""")
	List<ImageInteractionStatsRow> selectSummaries(@Param("ids") Collection<String> ids,
			@Param("userId") String userId);
}

class ImageInteractionStatsRow {
	private String imageId;
	private long favoriteCount;
	private long likeCount;
	private long commentCount;
	private boolean favoritedByMe;
	private boolean likedByMe;

	String imageId() { return imageId; }
	long favoriteCount() { return favoriteCount; }
	long likeCount() { return likeCount; }
	long commentCount() { return commentCount; }
	boolean favoritedByMe() { return favoritedByMe; }
	boolean likedByMe() { return likedByMe; }
}
