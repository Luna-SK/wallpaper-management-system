package com.luna.wallpaper.rbac;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RolePermissionMapper {

	@Select("""
			<script>
			select role_id, permission_id
			from role_permissions
			where role_id in
			<foreach collection="roleIds" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	List<RolePermissionLink> selectByRoleIds(@Param("roleIds") Collection<String> roleIds);

	@Delete("delete from role_permissions where role_id = #{roleId}")
	int deleteByRoleId(@Param("roleId") String roleId);

	@Insert("""
			<script>
			insert ignore into role_permissions (role_id, permission_id) values
			<foreach collection="permissionIds" item="permissionId" separator=",">
			  (#{roleId}, #{permissionId})
			</foreach>
			</script>
			""")
	int insertBatch(@Param("roleId") String roleId, @Param("permissionIds") Collection<String> permissionIds);
}

class RolePermissionLink {
	private String roleId;
	private String permissionId;

	String roleId() { return roleId; }
	String permissionId() { return permissionId; }
}
