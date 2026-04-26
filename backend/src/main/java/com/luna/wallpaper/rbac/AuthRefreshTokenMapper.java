package com.luna.wallpaper.rbac;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AuthRefreshTokenMapper extends BaseMapper<AuthRefreshToken> {

	@Select("select * from auth_refresh_tokens where token_hash = #{tokenHash} limit 1")
	AuthRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);

	@Update("update auth_refresh_tokens set revoked_at = now(6), updated_at = now(6) where id = #{id} and revoked_at is null")
	int revokeById(@Param("id") String id);

	@Update("""
			update auth_refresh_tokens
			set revoked_at = now(6), updated_at = now(6)
			where user_id = #{userId}
			  and revoked_at is null
			""")
	int revokeByUserId(@Param("userId") String userId);

	@Update("""
			update auth_refresh_tokens
			set revoked_at = now(6), updated_at = now(6)
			where user_id = #{userId}
			  and id <> #{sessionId}
			  and revoked_at is null
			""")
	int revokeOtherSessions(@Param("userId") String userId, @Param("sessionId") String sessionId);
}
