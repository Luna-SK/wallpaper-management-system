package com.luna.wallpaper.image;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ImageAssetMapper extends BaseMapper<ImageAsset> {

	@Select("select * from images where sha256 = #{sha256} and status <> #{status} limit 1")
	ImageAsset selectBySha256AndStatusNot(@Param("sha256") String sha256, @Param("status") ImageStatus status);

	default Optional<ImageAsset> findBySha256AndStatusNot(String sha256, ImageStatus status) {
		return Optional.ofNullable(selectBySha256AndStatusNot(sha256, status));
	}

	@Select("select * from images where status = #{status}")
	List<ImageAsset> selectByStatus(@Param("status") ImageStatus status);

	@Select("select count(*) from images where id = #{id} and status <> 'DELETED'")
	int countRetainedById(@Param("id") String id);

	@Select("select * from images where status = #{status} and deleted_at < #{deletedAt}")
	List<ImageAsset> selectByStatusAndDeletedAtBefore(@Param("status") ImageStatus status,
			@Param("deletedAt") LocalDateTime deletedAt);

	@Select("""
			<script>
			select count(*)
			from images image
			where
			  <if test="status == null">image.status &lt;&gt; 'DELETED'</if>
			  <if test="status != null">image.status = #{status}</if>
			  <if test="keyword != null">
			    and (lower(image.title) like lower(concat('%', #{keyword}, '%'))
			      or lower(image.original_filename) like lower(concat('%', #{keyword}, '%'))
			      or exists (
			        select 1
			        from image_tags image_tag
			        join tags tag on tag.id = image_tag.tag_id
			        join tag_groups tag_group on tag_group.id = tag.group_id
			        where image_tag.image_id = image.id
			          and tag.enabled = true
			          and tag_group.enabled = true
			          and lower(tag.name) like lower(concat('%', #{keyword}, '%'))
			      ))
			  </if>
			  <if test="categoryId != null">and image.category_id = #{categoryId}</if>
			  <if test="tagId != null">
			    and exists (
			      select 1
			      from image_tags image_tag
			      join tags tag on tag.id = image_tag.tag_id
			      join tag_groups tag_group on tag_group.id = tag.group_id
			      where image_tag.image_id = image.id
			        and image_tag.tag_id = #{tagId}
			        and tag.enabled = true
			        and tag_group.enabled = true
			    )
			  </if>
			  <if test="favoriteOnly">
			    and exists (
			      select 1
			      from image_favorites favorite
			      where favorite.image_id = image.id
			        and favorite.user_id = #{userId}
			    )
			  </if>
			</script>
			""")
	long countSearch(@Param("keyword") String keyword, @Param("categoryId") String categoryId,
			@Param("tagId") String tagId, @Param("status") ImageStatus status,
			@Param("favoriteOnly") boolean favoriteOnly, @Param("userId") String userId);

	@Select("""
			<script>
			select image.id
			from images image
			where
			  <if test="status == null">image.status &lt;&gt; 'DELETED'</if>
			  <if test="status != null">image.status = #{status}</if>
			  <if test="keyword != null">
			    and (lower(image.title) like lower(concat('%', #{keyword}, '%'))
			      or lower(image.original_filename) like lower(concat('%', #{keyword}, '%'))
			      or exists (
			        select 1
			        from image_tags image_tag
			        join tags tag on tag.id = image_tag.tag_id
			        join tag_groups tag_group on tag_group.id = tag.group_id
			        where image_tag.image_id = image.id
			          and tag.enabled = true
			          and tag_group.enabled = true
			          and lower(tag.name) like lower(concat('%', #{keyword}, '%'))
			      ))
			  </if>
			  <if test="categoryId != null">and image.category_id = #{categoryId}</if>
			  <if test="tagId != null">
			    and exists (
			      select 1
			      from image_tags image_tag
			      join tags tag on tag.id = image_tag.tag_id
			      join tag_groups tag_group on tag_group.id = tag.group_id
			      where image_tag.image_id = image.id
			        and image_tag.tag_id = #{tagId}
			        and tag.enabled = true
			        and tag_group.enabled = true
			    )
			  </if>
			  <if test="favoriteOnly">
			    and exists (
			      select 1
			      from image_favorites favorite
			      where favorite.image_id = image.id
			        and favorite.user_id = #{userId}
			    )
			  </if>
			order by image.created_at desc, image.id desc
			limit #{size} offset #{offset}
			</script>
			""")
	List<String> searchIds(@Param("keyword") String keyword, @Param("categoryId") String categoryId,
			@Param("tagId") String tagId, @Param("status") ImageStatus status,
			@Param("favoriteOnly") boolean favoriteOnly, @Param("userId") String userId,
			@Param("offset") long offset, @Param("size") long size);

	@Select("select count(*) from images where status <> #{status}")
	long countByStatusNot(@Param("status") ImageStatus status);

	@Select("select count(*) from images where created_at >= #{time} and status <> #{status}")
	long countByCreatedAtAfterAndStatusNot(@Param("time") LocalDateTime time, @Param("status") ImageStatus status);

	default long totalStorageBytes() {
		return totalStorageBytesExcluding(ImageStatus.DELETED);
	}

	@Select("select coalesce(sum(size_bytes), 0) from images where status <> #{status}")
	long totalStorageBytesExcluding(@Param("status") ImageStatus status);

	default long totalViews() {
		return totalViewsExcluding(ImageStatus.DELETED);
	}

	@Select("select coalesce(sum(view_count), 0) from images where status <> #{status}")
	long totalViewsExcluding(@Param("status") ImageStatus status);

	default long totalDownloads() {
		return totalDownloadsExcluding(ImageStatus.DELETED);
	}

	@Select("select coalesce(sum(download_count), 0) from images where status <> #{status}")
	long totalDownloadsExcluding(@Param("status") ImageStatus status);

	@Select("""
			select date(created_at) day, count(*) total
			from images
			where status <> #{status} and created_at >= #{startAt}
			group by date(created_at)
			order by date(created_at) asc
			""")
	List<ImageDailyCount> countUploadsByDaySinceExcluding(@Param("startAt") LocalDateTime startAt,
			@Param("status") ImageStatus status);

	default List<ImageDailyCount> countUploadsByDaySince(LocalDateTime startAt) {
		return countUploadsByDaySinceExcluding(startAt, ImageStatus.DELETED);
	}

	@Select("""
			select category.id category_id, category.name name, count(image.id) total
			from images image
			join categories category on category.id = image.category_id
			where image.status <> #{status}
			group by category.id, category.name
			order by count(image.id) desc, category.name asc
			""")
	List<ImageCategoryCount> countImagesByCategoryExcluding(@Param("status") ImageStatus status);

	default List<ImageCategoryCount> countImagesByCategory() {
		return countImagesByCategoryExcluding(ImageStatus.DELETED);
	}

	@Select("select count(*) from images where status <> #{status} and category_id is null")
	long countUncategorizedImagesExcluding(@Param("status") ImageStatus status);

	default long countUncategorizedImages() {
		return countUncategorizedImagesExcluding(ImageStatus.DELETED);
	}

	@Select("""
			select *
			from images
			where status <> #{status}
			order by view_count desc, created_at desc, id desc
			limit #{limit}
			""")
	List<ImageAsset> selectTopViewed(@Param("status") ImageStatus status, @Param("limit") int limit);

	@Select("""
			select *
			from images
			where status <> #{status}
			order by download_count desc, created_at desc, id desc
			limit #{limit}
			""")
	List<ImageAsset> selectTopDownloaded(@Param("status") ImageStatus status, @Param("limit") int limit);

	@Update("update images set view_count = view_count + 1, updated_at = now(6) where id = #{id}")
	int incrementViewCount(@Param("id") String id);

	@Update("update images set download_count = download_count + 1, updated_at = now(6) where id = #{id}")
	int incrementDownloadCount(@Param("id") String id);

	@Select("""
			<script>
			select *
			from images
			where id in
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	List<ImageAsset> selectImagesByIds(@Param("ids") Collection<String> ids);
}

class ImageDailyCount {
	private LocalDate day;
	private long total;

	LocalDate day() { return day; }
	long total() { return total; }
}

class ImageCategoryCount {
	private String categoryId;
	private String name;
	private long total;

	String categoryId() { return categoryId; }
	String name() { return name; }
	long total() { return total; }
}
