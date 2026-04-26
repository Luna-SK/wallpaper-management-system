package com.luna.wallpaper.rbac;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserRoleMapper {

	@Select("""
			<script>
			select user_id, role_id
			from user_roles
			where user_id in
			<foreach collection="userIds" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	List<UserRoleLink> selectByUserIds(@Param("userIds") Collection<String> userIds);

	@Select("select role_id, count(*) user_count from user_roles group by role_id")
	List<RoleUserCount> countUsersByRole();

	@Select("select count(*) from user_roles where role_id = #{roleId}")
	int countByRoleId(@Param("roleId") String roleId);

	@Delete("delete from user_roles where user_id = #{userId}")
	int deleteByUserId(@Param("userId") String userId);

	@Insert("""
			<script>
			insert ignore into user_roles (user_id, role_id) values
			<foreach collection="roleIds" item="roleId" separator=",">
			  (#{userId}, #{roleId})
			</foreach>
			</script>
			""")
	int insertBatch(@Param("userId") String userId, @Param("roleIds") Collection<String> roleIds);
}

class UserRoleLink {
	private String userId;
	private String roleId;

	String userId() { return userId; }
	String roleId() { return roleId; }
}

class RoleUserCount {
	private String roleId;
	private int userCount;

	String roleId() { return roleId; }
	int userCount() { return userCount; }
}
