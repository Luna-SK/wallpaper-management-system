package com.luna.wallpaper.rbac;

import java.util.List;

import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface PermissionMapper extends BaseMapper<Permission> {

	@Select("select * from permissions order by resource asc, action asc")
	List<Permission> selectOrdered();
}
