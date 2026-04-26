package com.luna.wallpaper.taxonomy;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface TagGroupMapper extends BaseMapper<TagGroup> {

	@Select("select * from tag_groups order by sort_order asc, name asc")
	List<TagGroup> selectOrdered();

	@Select("select * from tag_groups where code = #{code} limit 1")
	TagGroup selectByCode(@Param("code") String code);

	@Select("select 1 from tag_groups where code = #{code} and id <> #{id} limit 1")
	Integer existsByCodeExcludingId(@Param("code") String code, @Param("id") String id);

	@Select("select count(*) from tags where group_id = #{groupId}")
	long countTagsByGroupId(@Param("groupId") String groupId);

	@Select("""
			select count(*)
			from image_tags image_tag
			join tags tag on tag.id = image_tag.tag_id
			where tag.group_id = #{groupId}
			""")
	long countImageRefsByGroupId(@Param("groupId") String groupId);

	@Select("""
			select count(*)
			from upload_batch_tags batch_tag
			join tags tag on tag.id = batch_tag.tag_id
			where tag.group_id = #{groupId}
			""")
	long countUploadBatchRefsByGroupId(@Param("groupId") String groupId);

	@Delete("""
			delete image_tag
			from image_tags image_tag
			join tags tag on tag.id = image_tag.tag_id
			where tag.group_id = #{groupId}
			""")
	int deleteImageRefsByGroupId(@Param("groupId") String groupId);

	@Delete("""
			delete batch_tag
			from upload_batch_tags batch_tag
			join tags tag on tag.id = batch_tag.tag_id
			where tag.group_id = #{groupId}
			""")
	int deleteUploadBatchRefsByGroupId(@Param("groupId") String groupId);

	@Delete("delete from tags where group_id = #{groupId}")
	int deleteTagsByGroupId(@Param("groupId") String groupId);

	default Optional<TagGroup> findByCode(String code) {
		return Optional.ofNullable(selectByCode(code));
	}

	default boolean hasCodeExcludingId(String code, String id) {
		return existsByCodeExcludingId(code, id) != null;
	}
}
