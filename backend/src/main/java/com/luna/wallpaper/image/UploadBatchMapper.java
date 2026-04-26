package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface UploadBatchMapper extends BaseMapper<UploadBatch> {

	@Select("""
			<script>
			select *
			from upload_batches
			where status in
			<foreach collection="statuses" item="status" open="(" separator="," close=")">
			  #{status}
			</foreach>
			and expires_at &lt; #{expiresAt}
			</script>
			""")
	List<UploadBatch> selectExpired(@Param("statuses") Collection<String> statuses,
			@Param("expiresAt") LocalDateTime expiresAt);
}
