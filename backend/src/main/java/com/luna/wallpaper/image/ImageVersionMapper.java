package com.luna.wallpaper.image;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ImageVersionMapper extends BaseMapper<ImageVersion> {

	@Select("select * from image_versions where image_id = #{imageId} order by version_no desc")
	List<ImageVersion> selectByImageIdOrdered(@Param("imageId") String imageId);

	@Select("select * from image_versions where image_id = #{imageId} and id = #{versionId}")
	ImageVersion selectByImageIdAndId(@Param("imageId") String imageId, @Param("versionId") String versionId);

	@Select("select * from image_versions where image_id = #{imageId} order by version_no desc limit 1")
	ImageVersion selectLatestByImageId(@Param("imageId") String imageId);

	default Optional<ImageVersion> findLatestByImageId(String imageId) {
		return Optional.ofNullable(selectLatestByImageId(imageId));
	}

	@Update("update image_versions set current_flag = false where image_id = #{imageId}")
	int clearCurrentFlag(@Param("imageId") String imageId);

	@Update("update image_versions set current_flag = true where id = #{id}")
	int markCurrent(@Param("id") String id);

	@Delete("delete from image_versions where id = #{id}")
	int deleteVersionById(@Param("id") String id);

	@Delete("""
			<script>
			delete from image_versions where id in
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	int deleteVersionsByIds(@Param("ids") Collection<String> ids);

	@Select("""
			select original_object_key object_key from image_versions
			union all select thumbnail_object_key object_key from image_versions
			union all select high_preview_object_key object_key from image_versions
			union all select standard_preview_object_key object_key from image_versions
			""")
	List<String> selectReferencedObjectKeys();

	@Select("""
			select image_id
			from image_versions
			group by image_id
			having count(*) > #{maxRetained}
			""")
	List<String> selectImageIdsExceedingRetainedLimit(@Param("maxRetained") int maxRetained);
}
