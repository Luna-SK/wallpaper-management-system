package com.luna.wallpaper.image;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface UploadBatchItemMapper extends BaseMapper<UploadBatchItem> {

	@Select("select * from upload_batch_items where batch_id = #{batchId} order by created_at asc, id asc")
	List<UploadBatchItem> selectByBatchIdOrdered(@Param("batchId") String batchId);

	@Select("""
			<script>
			select *
			from upload_batch_items
			where batch_id in
			<foreach collection="batchIds" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	List<UploadBatchItem> selectByBatchIds(@Param("batchIds") Collection<String> batchIds);

	@Select("""
			<script>
			select *
			from upload_batch_items
			where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			</script>
			""")
	List<UploadBatchItem> selectByStatuses(@Param("statuses") Collection<String> statuses);

	@Select("""
			<script>
			select original_object_key object_key from upload_batch_items where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			union all
			select thumbnail_object_key object_key from upload_batch_items where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			union all
			select high_preview_object_key object_key from upload_batch_items where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			union all
			select standard_preview_object_key object_key from upload_batch_items where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			</script>
			""")
	List<String> selectReferencedObjectKeysByStatuses(@Param("statuses") Collection<String> statuses);
}
