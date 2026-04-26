package com.luna.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AuditLogMapper extends BaseMapper<AuditLog> {

	@Select("""
			<script>
			select count(*)
			from audit_logs log
			where
			  <if test="keyword != null">
			    (lower(log.action) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_type) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_id) like lower(concat('%', #{keyword}, '%')))
			  </if>
			  <if test="keyword == null">1 = 1</if>
			  <if test="startAt != null">and log.created_at &gt;= #{startAt}</if>
			  <if test="endAt != null">and log.created_at &lt;= #{endAt}</if>
			</script>
			""")
	long countSearch(@Param("keyword") String keyword, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt);

	@Select("""
			<script>
			select *
			from audit_logs log
			where
			  <if test="keyword != null">
			    (lower(log.action) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_type) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_id) like lower(concat('%', #{keyword}, '%')))
			  </if>
			  <if test="keyword == null">1 = 1</if>
			  <if test="startAt != null">and log.created_at &gt;= #{startAt}</if>
			  <if test="endAt != null">and log.created_at &lt;= #{endAt}</if>
			order by log.created_at desc, log.id desc
			limit #{size} offset #{offset}
			</script>
			""")
	List<AuditLog> search(@Param("keyword") String keyword, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt, @Param("offset") long offset, @Param("size") long size);

	@Select("""
			<script>
			select *
			from audit_logs log
			where
			  <if test="keyword != null">
			    (lower(log.action) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_type) like lower(concat('%', #{keyword}, '%'))
			      or lower(log.target_id) like lower(concat('%', #{keyword}, '%')))
			  </if>
			  <if test="keyword == null">1 = 1</if>
			  <if test="startAt != null">and log.created_at &gt;= #{startAt}</if>
			  <if test="endAt != null">and log.created_at &lt;= #{endAt}</if>
			order by log.created_at desc, log.id desc
			</script>
			""")
	List<AuditLog> selectForExport(@Param("keyword") String keyword, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt);

	@Select("select count(*) from audit_logs where created_at < #{cutoffTime}")
	long countByCreatedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

	@Select("""
			<script>
			select *
			from audit_logs
			where created_at &lt; #{cutoffTime}
			  <if test="lastCreatedAt != null">
			    and (created_at &gt; #{lastCreatedAt}
			      or (created_at = #{lastCreatedAt} and id &gt; #{lastId}))
			  </if>
			order by created_at asc, id asc
			limit #{limit}
			</script>
			""")
	List<AuditLog> selectArchiveBatch(@Param("cutoffTime") LocalDateTime cutoffTime,
			@Param("lastCreatedAt") LocalDateTime lastCreatedAt, @Param("lastId") String lastId,
			@Param("limit") int limit);

	@Delete("""
			<script>
			delete from audit_logs where id in
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	int deleteArchiveBatchByIds(@Param("ids") Collection<String> ids);
}
