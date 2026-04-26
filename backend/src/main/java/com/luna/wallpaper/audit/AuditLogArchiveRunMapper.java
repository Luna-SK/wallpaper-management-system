package com.luna.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AuditLogArchiveRunMapper extends BaseMapper<AuditLogArchiveRun> {

	@Select("select count(*) from audit_log_archives")
	long countAll();

	@Select("""
			select *
			from audit_log_archives
			order by started_at desc, id desc
			limit #{size} offset #{offset}
			""")
	List<AuditLogArchiveRun> selectPageOrdered(@Param("offset") long offset, @Param("size") long size);

	@Select("select count(*) from audit_log_archives where started_at < #{cutoffTime}")
	long countByStartedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

	@Delete("delete from audit_log_archives where started_at < #{cutoffTime}")
	int deleteByStartedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
