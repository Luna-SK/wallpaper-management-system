package com.luna.wallpaper.rbac;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface RoleMapper extends BaseMapper<Role> {

	@Select("select * from roles order by code asc")
	List<Role> selectOrdered();

	@Select("select * from roles where code = #{code} limit 1")
	Role selectByCode(@Param("code") String code);

	@Select("select 1 from roles where code = #{code} and id <> #{id} limit 1")
	Integer existsByCodeExcludingId(@Param("code") String code, @Param("id") String id);

	default Optional<Role> findByCode(String code) {
		return Optional.ofNullable(selectByCode(code));
	}

	default boolean hasCodeExcludingId(String code, String id) {
		return existsByCodeExcludingId(code, id) != null;
	}
}
