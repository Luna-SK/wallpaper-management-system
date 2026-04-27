package com.luna.wallpaper.rbac;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AppUserMapper extends BaseMapper<AppUser> {

	@Select("select * from app_users order by username asc")
	List<AppUser> selectOrdered();

	@Select("select * from app_users where username = #{username} limit 1")
	AppUser selectByUsername(@Param("username") String username);

	@Select("""
			select *
			from app_users
			where email = #{email}
			  and status = 'ACTIVE'
			limit 1
			""")
	AppUser selectActiveByEmail(@Param("email") String email);

	@Select("select 1 from app_users where username = #{username} and id <> #{id} limit 1")
	Integer existsByUsernameExcludingId(@Param("username") String username, @Param("id") String id);

	@Select("select 1 from app_users where email = #{email} limit 1")
	Integer existsByEmail(@Param("email") String email);

	@Select("select 1 from app_users where email = #{email} and id <> #{id} limit 1")
	Integer existsByEmailExcludingId(@Param("email") String email, @Param("id") String id);

	default Optional<AppUser> findByUsername(String username) {
		return Optional.ofNullable(selectByUsername(username));
	}

	default boolean hasUsernameExcludingId(String username, String id) {
		return existsByUsernameExcludingId(username, id) != null;
	}

	default boolean hasEmail(String email) {
		return existsByEmail(email) != null;
	}

	default boolean hasEmailExcludingId(String email, String id) {
		return existsByEmailExcludingId(email, id) != null;
	}
}
