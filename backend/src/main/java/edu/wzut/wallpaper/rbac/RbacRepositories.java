package edu.wzut.wallpaper.rbac;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, String> {
	List<AppUser> findAllByOrderByUsernameAsc();
	Optional<AppUser> findByUsername(String username);
	boolean existsByUsernameAndIdNot(String username, String id);
}

interface RoleRepository extends JpaRepository<Role, String> {
	List<Role> findAllByOrderByCodeAsc();
	List<Role> findByIdIn(Collection<String> ids);
	Optional<Role> findByCode(String code);
	boolean existsByCodeAndIdNot(String code, String id);
}

interface PermissionRepository extends JpaRepository<Permission, String> {
	List<Permission> findAllByOrderByResourceAscActionAsc();
	List<Permission> findByIdIn(Collection<String> ids);
}
