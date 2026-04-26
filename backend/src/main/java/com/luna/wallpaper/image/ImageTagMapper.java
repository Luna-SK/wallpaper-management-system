package com.luna.wallpaper.image;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImageTagMapper {

	@Select("""
			<script>
			select image_id, tag_id
			from image_tags
			where image_id in
			<foreach collection="imageIds" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	List<ImageTagLink> selectByImageIds(@Param("imageIds") Collection<String> imageIds);

	@Delete("delete from image_tags where image_id = #{imageId}")
	int deleteByImageId(@Param("imageId") String imageId);

	@Insert("""
			<script>
			insert ignore into image_tags (image_id, tag_id) values
			<foreach collection="tagIds" item="tagId" separator=",">
			  (#{imageId}, #{tagId})
			</foreach>
			</script>
			""")
	int insertBatch(@Param("imageId") String imageId, @Param("tagIds") Collection<String> tagIds);
}

class ImageTagLink {
	private String imageId;
	private String tagId;

	String imageId() { return imageId; }
	String tagId() { return tagId; }
}
