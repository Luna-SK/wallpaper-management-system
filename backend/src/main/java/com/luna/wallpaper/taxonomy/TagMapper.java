package com.luna.wallpaper.taxonomy;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface TagMapper extends BaseMapper<Tag> {

	@Select("""
			select tag.*, tag_group.name group_name
			from tags tag
			join tag_groups tag_group on tag_group.id = tag.group_id
			order by tag_group.sort_order asc, tag_group.name asc, tag.sort_order asc, tag.name asc
			""")
	List<Tag> selectOrderedWithGroup();

	@Select("""
			select tag.*, tag_group.name group_name
			from tags tag
			join tag_groups tag_group on tag_group.id = tag.group_id
			where tag.group_id = #{groupId}
			order by tag.sort_order asc, tag.name asc
			""")
	List<Tag> selectByGroupIdOrdered(@Param("groupId") String groupId);

	@Select("select 1 from tags where group_id = #{groupId} and name = #{name} and id <> #{id} limit 1")
	Integer existsByGroupAndNameExcludingId(@Param("groupId") String groupId, @Param("name") String name,
			@Param("id") String id);

	@Select("select count(*) from tags where group_id = #{groupId}")
	long countByGroupId(@Param("groupId") String groupId);

	@Select("select count(*) from image_tags where tag_id = #{tagId}")
	long countImageRefsByTagId(@Param("tagId") String tagId);

	@Select("select count(*) from upload_batch_tags where tag_id = #{tagId}")
	long countUploadBatchRefsByTagId(@Param("tagId") String tagId);

	@Delete("delete from image_tags where tag_id = #{tagId}")
	int deleteImageRefsByTagId(@Param("tagId") String tagId);

	@Delete("delete from upload_batch_tags where tag_id = #{tagId}")
	int deleteUploadBatchRefsByTagId(@Param("tagId") String tagId);

	@Update("update tags set enabled = false, updated_at = now(6) where group_id = #{groupId} and enabled = true")
	int disableByGroupId(@Param("groupId") String groupId);

	default boolean hasGroupNameExcludingId(String groupId, String name, String id) {
		return existsByGroupAndNameExcludingId(groupId, name, id) != null;
	}
}
