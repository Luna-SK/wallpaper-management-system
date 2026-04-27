package com.luna.wallpaper.rbac;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetToken> {

	@Select("select * from password_reset_tokens where token_hash = #{tokenHash} limit 1")
	PasswordResetToken selectByTokenHash(@Param("tokenHash") String tokenHash);

	@Update("""
			update password_reset_tokens
			set used_at = now(6), updated_at = now(6)
			where user_id = #{userId}
			  and used_at is null
			  and expires_at > now(6)
			""")
	int consumeOpenTokensByUserId(@Param("userId") String userId);
}
