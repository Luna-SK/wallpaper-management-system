<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import {
  disableRole,
  disableUser,
  enableRole,
  enableUser,
  getPermissions,
  getRoles,
  getUsers,
  purgeRole,
  purgeUser,
  resetUserPassword,
  saveRole,
  saveUser,
  updateRolePermissions,
  updateUserRoles,
  type Permission,
  type RbacReferenceImpact,
  type Role,
  type User,
  type UserStatus,
} from '../api/users'
import { useAuthStore } from '../stores/auth'
import { useDialogEnterSubmit } from '../utils/dialogEnterSubmit'

interface ApiErrorBody {
  code?: string
  message?: string
  data?: RbacReferenceImpact
}

type UserStatusFilter = 'ALL' | UserStatus
type RoleStatusFilter = 'ALL' | 'ENABLED' | 'DISABLED'

const auth = useAuthStore()
const loading = ref(false)
const userKeyword = ref('')
const roleKeyword = ref('')
const userStatusFilter = ref<UserStatusFilter>('ALL')
const roleStatusFilter = ref<RoleStatusFilter>('ALL')
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const userPagination = reactive({ page: 1, size: 20 })
const rolePagination = reactive({ page: 1, size: 20 })
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const resetPasswordVisible = ref(false)
const userForm = reactive<{
  id: string
  username: string
  displayName: string
  email: string
  phone: string
  status: UserStatus
  initialPassword: string
}>({ id: '', username: '', displayName: '', email: '', phone: '', status: 'ACTIVE', initialPassword: '' })
const roleForm = reactive({ id: '', code: '', name: '', enabled: true })
const resetPasswordForm = reactive({ userId: '', username: '', newPassword: '' })
const selectedRoleIds = ref<string[]>([])
const selectedPermissionIds = ref<string[]>([])
const canManageUsers = computed(() => auth.hasPermission('user:manage'))
const canManageRoles = computed(() => auth.hasPermission('role:manage'))

const filteredUsers = computed(() => {
  const query = userKeyword.value.trim()
  return users.value.filter((user) => {
    const matchesStatus = userStatusFilter.value === 'ALL' || user.status === userStatusFilter.value
    const matchesKeyword = !query || [user.username, user.displayName, user.roles.map((role) => role.name).join(',')].join(' ').includes(query)
    return matchesStatus && matchesKeyword
  })
})

const filteredRoles = computed(() => {
  const query = roleKeyword.value.trim()
  return roles.value.filter((role) => {
    const matchesStatus = roleStatusFilter.value === 'ALL'
      || (roleStatusFilter.value === 'ENABLED' ? role.enabled : !role.enabled)
    const matchesKeyword = !query || `${role.code} ${role.name}`.includes(query)
    return matchesStatus && matchesKeyword
  })
})

function maxPage(total: number, pageSize: number) {
  return Math.max(1, Math.ceil(total / pageSize))
}

function clampPage(page: number, pageSize: number, total: number) {
  return Math.min(Math.max(page, 1), maxPage(total, pageSize))
}

function clampUserPage() {
  userPagination.page = clampPage(userPagination.page, userPagination.size, filteredUsers.value.length)
}

function clampRolePage() {
  rolePagination.page = clampPage(rolePagination.page, rolePagination.size, filteredRoles.value.length)
}

const pagedUsers = computed(() => {
  const page = clampPage(userPagination.page, userPagination.size, filteredUsers.value.length)
  const start = (page - 1) * userPagination.size
  return filteredUsers.value.slice(start, start + userPagination.size)
})

const pagedRoles = computed(() => {
  const page = clampPage(rolePagination.page, rolePagination.size, filteredRoles.value.length)
  const start = (page - 1) * rolePagination.size
  return filteredRoles.value.slice(start, start + rolePagination.size)
})

const resourceLabels: Record<string, string> = {
  image: '图片',
  taxonomy: '分类标签',
  user: '用户',
  role: '角色',
  audit: '审计',
  setting: '系统设置',
  backup: '备份',
}

const actionLabels: Record<string, string> = {
  view: '查看',
  upload: '上传',
  edit: '编辑',
  delete: '删除',
  manage: '管理',
}

const resourceTypes: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  image: 'success',
  taxonomy: 'primary',
  user: 'warning',
  role: 'danger',
  audit: 'info',
  setting: 'warning',
  backup: 'info',
}

function permissionSummaryGroups(role: Role) {
  const grouped = role.permissions.reduce<Record<string, string[]>>((result, permission) => {
    result[permission.resource] = [...(result[permission.resource] ?? []), actionLabels[permission.action] ?? permission.action]
    return result
  }, {})

  return Object.entries(grouped).map(([resource, actions]) => ({
    resource,
    label: resourceLabels[resource] ?? resource,
    actions,
    type: resourceTypes[resource] ?? 'info',
  }))
}

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function referenceImpact(error: unknown) {
  if (isAxiosError<ApiErrorBody>(error) && error.response?.status === 409 && error.response.data?.code === 'REFERENCE_EXISTS') {
    return error.response.data.data ?? null
  }
  return null
}

function ensurePermission(allowed: boolean) {
  if (!allowed) {
    ElMessage.warning('当前用户没有此操作权限')
    return false
  }
  return true
}

function handleUserPageSizeChange(size: number) {
  userPagination.size = size
  userPagination.page = 1
  clampUserPage()
}

function handleUserPageChange(page: number) {
  userPagination.page = page
  clampUserPage()
}

function handleRolePageSizeChange(size: number) {
  rolePagination.size = size
  rolePagination.page = 1
  clampRolePage()
}

function handleRolePageChange(page: number) {
  rolePagination.page = page
  clampRolePage()
}

async function refresh() {
  loading.value = true
  try {
    const [userRows, roleRows, permissionRows] = await Promise.all([
      canManageUsers.value ? getUsers() : Promise.resolve([]),
      canManageUsers.value || canManageRoles.value ? getRoles() : Promise.resolve([]),
      canManageUsers.value || canManageRoles.value ? getPermissions() : Promise.resolve([]),
    ])
    users.value = userRows
    roles.value = roleRows
    permissions.value = permissionRows
  } catch (error) {
    ElMessage.error(errorMessage(error, '用户权限数据加载失败'))
  } finally {
    loading.value = false
  }
}

function openUser(row?: User) {
  if (!ensurePermission(canManageUsers.value)) return
  userForm.id = row?.id ?? ''
  userForm.username = row?.username ?? ''
  userForm.displayName = row?.displayName ?? ''
  userForm.email = row?.email ?? ''
  userForm.phone = row?.phone ?? ''
  userForm.status = row?.status ?? 'ACTIVE'
  userForm.initialPassword = ''
  userDialogVisible.value = true
}

async function submitUser() {
  if (!ensurePermission(canManageUsers.value)) return
  try {
    await saveUser(userForm)
    ElMessage.success('用户已保存')
    userDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '用户保存失败'))
  }
}

function openResetPassword(row: User) {
  if (!ensurePermission(canManageUsers.value)) return
  resetPasswordForm.userId = row.id
  resetPasswordForm.username = row.username
  resetPasswordForm.newPassword = ''
  resetPasswordVisible.value = true
}

async function submitResetPassword() {
  if (!ensurePermission(canManageUsers.value)) return
  try {
    await resetUserPassword(resetPasswordForm.userId, resetPasswordForm.newPassword)
    ElMessage.success('密码已重置')
    resetPasswordVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error, '密码重置失败'))
  }
}

function openAssignRoles(row: User) {
  if (!ensurePermission(canManageUsers.value)) return
  userForm.id = row.id
  userForm.username = row.username
  selectedRoleIds.value = row.roles.map((role) => role.id)
  assignDialogVisible.value = true
}

async function submitUserRoles() {
  if (!ensurePermission(canManageUsers.value)) return
  try {
    await updateUserRoles(userForm.id, selectedRoleIds.value)
    ElMessage.success('角色已分配')
    assignDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '角色分配失败'))
  }
}

async function toggleUser(row: User) {
  if (!ensurePermission(canManageUsers.value)) return
  const disabling = row.status === 'ACTIVE'
  if (disabling) {
    try {
      await ElMessageBox.confirm(`确定停用用户“${row.username}”？停用后该用户无法登录，现有会话也会失效。`, '停用用户', {
        type: 'warning',
      })
    } catch {
      return
    }
  }
  try {
    if (disabling) {
      await disableUser(row.id)
    } else {
      await enableUser(row.id)
    }
    ElMessage.success(disabling ? '用户已停用' : '用户已启用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, disabling ? '用户停用失败' : '用户启用失败'))
  }
}

async function purgeUserRow(row: User) {
  if (!ensurePermission(canManageUsers.value)) return
  try {
    await ElMessageBox.confirm(`彻底删除用户“${row.username}”？此操作不可恢复。`, '彻底删除用户', {
      confirmButtonText: '彻底删除',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await purgeUser(row.id)
    ElMessage.success('用户已彻底删除')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '用户彻底删除失败'))
  }
}

function openRole(row?: Role) {
  if (!ensurePermission(canManageRoles.value)) return
  roleForm.id = row?.id ?? ''
  roleForm.code = row?.code ?? ''
  roleForm.name = row?.name ?? ''
  roleForm.enabled = row?.enabled ?? true
  roleDialogVisible.value = true
}

async function submitRole() {
  if (!ensurePermission(canManageRoles.value)) return
  try {
    await saveRole(roleForm)
    ElMessage.success('角色已保存')
    roleDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '角色保存失败'))
  }
}

function openRolePermissions(row: Role) {
  if (!ensurePermission(canManageRoles.value)) return
  roleForm.id = row.id
  roleForm.name = row.name
  selectedPermissionIds.value = row.permissions.map((permission) => permission.id)
  permissionDialogVisible.value = true
}

async function submitRolePermissions() {
  if (!ensurePermission(canManageRoles.value)) return
  try {
    await updateRolePermissions(roleForm.id, selectedPermissionIds.value)
    ElMessage.success('权限已保存')
    permissionDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '权限保存失败'))
  }
}

async function toggleRole(row: Role) {
  if (!ensurePermission(canManageRoles.value)) return
  const disabling = row.enabled
  if (disabling) {
    try {
      await ElMessageBox.confirm(`确定停用角色“${row.name}”？停用后拥有该角色的用户将立即失去对应权限。`, '停用角色', {
        type: 'warning',
      })
    } catch {
      return
    }
  }
  try {
    if (disabling) {
      await disableRole(row.id)
    } else {
      await enableRole(row.id)
    }
    ElMessage.success(disabling ? '角色已停用' : '角色已启用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, disabling ? '角色停用失败' : '角色启用失败'))
  }
}

async function purgeRoleRow(row: Role) {
  if (!ensurePermission(canManageRoles.value)) return
  try {
    await ElMessageBox.confirm(`彻底删除角色“${row.name}”？此操作不可恢复。`, '彻底删除角色', {
      confirmButtonText: '彻底删除',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await purgeRole(row.id)
    ElMessage.success('角色已彻底删除')
    await refresh()
  } catch (error) {
    const impact = referenceImpact(error)
    if (impact) {
      ElMessage.warning(`角色仍有 ${impact.userCount} 个用户引用，请先取消分配`)
      return
    }
    ElMessage.error(errorMessage(error, '角色彻底删除失败'))
  }
}

useDialogEnterSubmit(userDialogVisible, submitUser)
useDialogEnterSubmit(assignDialogVisible, submitUserRoles)
useDialogEnterSubmit(roleDialogVisible, submitRole)
useDialogEnterSubmit(permissionDialogVisible, submitRolePermissions)
useDialogEnterSubmit(resetPasswordVisible, submitResetPassword)

watch([userKeyword, userStatusFilter], () => {
  userPagination.page = 1
})

watch([roleKeyword, roleStatusFilter], () => {
  rolePagination.page = 1
})

watch(() => [filteredUsers.value.length, userPagination.size] as const, clampUserPage)
watch(() => [filteredRoles.value.length, rolePagination.size] as const, clampRolePage)

onMounted(refresh)
</script>

<template>
  <section class="workspace-page">
    <div v-loading="loading" class="surface surface-pad workspace-scroll-region">
      <el-tabs>
        <el-tab-pane v-if="canManageUsers" label="用户">
          <div class="toolbar-row">
            <el-radio-group v-model="userStatusFilter">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="ACTIVE">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
            <el-input v-model="userKeyword" placeholder="搜索用户名、姓名或角色" :prefix-icon="Search" style="max-width: 320px" />
            <el-button type="primary" :icon="Plus" @click="openUser()">新增用户</el-button>
          </div>

          <el-table :data="pagedUsers" stripe>
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="displayName" label="姓名" width="160" />
            <el-table-column label="角色" min-width="220">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles" :key="role.id" style="margin-right: 6px">{{ role.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="380" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openUser(row)">编辑</el-button>
                <el-button link type="primary" @click="openAssignRoles(row)">分配角色</el-button>
                <el-button link type="primary" @click="openResetPassword(row)">重置密码</el-button>
                <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleUser(row)">
                  {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
                </el-button>
                <el-button v-if="row.status === 'DISABLED'" link type="danger" @click="purgeUserRow(row)">彻底删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="userPagination.page"
              v-model:page-size="userPagination.size"
              :page-sizes="[20, 50, 100]"
              :total="filteredUsers.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleUserPageSizeChange"
              @current-change="handleUserPageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canManageRoles" label="角色">
          <div class="toolbar-row">
            <el-radio-group v-model="roleStatusFilter">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
            <el-input v-model="roleKeyword" placeholder="搜索角色编码或名称" :prefix-icon="Search" style="max-width: 320px" />
            <el-button type="primary" :icon="Plus" @click="openRole()">新增角色</el-button>
          </div>

          <el-table :data="pagedRoles" stripe>
            <el-table-column prop="code" label="角色编码" width="160" />
            <el-table-column prop="name" label="角色名称" width="160" />
            <el-table-column label="权限摘要" min-width="420">
              <template #default="{ row }">
                <div class="permission-summary">
                  <el-tag v-for="group in permissionSummaryGroups(row)" :key="group.resource" :type="group.type" effect="light" class="permission-summary-tag">
                    <span class="permission-summary-resource">{{ group.label }}</span>
                    <span class="permission-summary-actions">{{ group.actions.join(' / ') }}</span>
                  </el-tag>
                  <el-tag v-if="row.permissions.length === 0" type="info" effect="plain">未配置权限</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="userCount" label="用户数" width="100" />
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRole(row)">编辑</el-button>
                <el-button link type="primary" @click="openRolePermissions(row)">配置权限</el-button>
                <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleRole(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
                <el-button v-if="!row.enabled" link type="danger" @click="purgeRoleRow(row)">彻底删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="rolePagination.page"
              v-model:page-size="rolePagination.size"
              :page-sizes="[20, 50, 100]"
              :total="filteredRoles.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleRolePageSizeChange"
              @current-change="handleRolePageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canManageRoles" label="权限">
          <el-table :data="permissions" stripe>
            <el-table-column prop="code" label="权限编码" width="180" />
            <el-table-column prop="name" label="权限名称" width="160" />
            <el-table-column prop="resource" label="资源" width="140" />
            <el-table-column prop="action" label="动作" width="140" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <el-empty v-if="!canManageUsers && !canManageRoles" description="当前用户没有用户或角色管理权限" />
    </div>

    <el-dialog v-model="userDialogVisible" title="用户" width="480px">
      <el-form label-width="86px">
        <el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="Boolean(userForm.id)" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item v-if="!userForm.id" label="初始密码"><el-input v-model="userForm.initialPassword" type="password" show-password /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="userForm.phone" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialogVisible = false">取消</el-button><el-button type="primary" @click="submitUser">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="resetPasswordVisible" title="重置密码" width="460px">
      <el-form label-width="86px">
        <el-form-item label="用户名"><el-input v-model="resetPasswordForm.username" disabled /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="resetPasswordForm.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="resetPasswordVisible = false">取消</el-button><el-button type="primary" @click="submitResetPassword">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" title="分配角色" width="460px">
      <div>
        <el-select v-model="selectedRoleIds" multiple style="width: 100%">
          <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
        </el-select>
      </div>
      <template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button type="primary" @click="submitUserRoles">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="角色" width="460px">
      <el-form label-width="86px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.code" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.name" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="roleDialogVisible = false">取消</el-button><el-button type="primary" @click="submitRole">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionDialogVisible" title="配置权限" width="620px">
      <div>
        <el-checkbox-group v-model="selectedPermissionIds" class="permission-checks">
          <el-checkbox v-for="permission in permissions" :key="permission.id" :label="permission.name" :value="permission.id">
            {{ permission.name }}（{{ permission.code }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer><el-button @click="permissionDialogVisible = false">取消</el-button><el-button type="primary" @click="submitRolePermissions">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.permission-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 0;
}

.permission-summary-tag {
  height: auto;
  line-height: 1.6;
  padding: 4px 8px;
}

.permission-summary-resource {
  font-weight: 700;
  margin-right: 6px;
}

.permission-summary-actions {
  opacity: 0.82;
}

.permission-checks {
  display: grid;
  gap: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .pagination-row {
    justify-content: flex-start;
  }
}
</style>
