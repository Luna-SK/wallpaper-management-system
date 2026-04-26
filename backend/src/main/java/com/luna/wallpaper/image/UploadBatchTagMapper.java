package com.luna.wallpaper.image;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UploadBatchTagMapper {

	@Select("select tag_id from upload_batch_tags where batch_id = #{batchId} order by tag_id asc")
	List<String> selectTagIdsByBatchId(@Param("batchId") String batchId);

	@Delete("delete from upload_batch_tags where batch_id = #{batchId}")
	int deleteByBatchId(@Param("batchId") String batchId);

	@Insert("""
			<script>
			insert ignore into upload_batch_tags (batch_id, tag_id) values
			<foreach collection="tagIds" item="tagId" separator=",">
			  (#{batchId}, #{tagId})
			</foreach>
			</script>
			""")
	int insertBatch(@Param("batchId") String batchId, @Param("tagIds") Collection<String> tagIds);
}
