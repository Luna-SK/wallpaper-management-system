<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import {
  getPermissions,
  getRoles,
  getUsers,
  saveRole,
  saveUser,
  updateRolePermissions,
  updateUserRoles,
  type Permission,
  type Role,
  type User,
} from '../api/users'

const loading = ref(false)
const userKeyword = ref('')
const roleKeyword = ref('')
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const userForm = reactive({ id: '', username: '', displayName: '', email: '', phone: '', status: 'ACTIVE' })
const roleForm = reactive({ id: '', code: '', name: '', enabled: true })
const selectedRoleIds = ref<string[]>([])
const selectedPermissionIds = ref<string[]>([])

const filteredUsers = computed(() => {
  const query = userKeyword.value.trim()
  return users.value.filter((user) => !query || [user.username, user.displayName, user.roles.map((role) => role.name).join(',')].join(' ').includes(query))
})

const filteredRoles = computed(() => {
  const query = roleKeyword.value.trim()
  return roles.value.filter((role) => !query || `${role.code} ${role.name}`.includes(query))
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
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

async function refresh() {
  loading.value = true
  try {
    const [userRows, roleRows, permissionRows] = await Promise.all([getUsers(), getRoles(), getPermissions()])
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
  userForm.id = row?.id ?? ''
  userForm.username = row?.username ?? ''
  userForm.displayName = row?.displayName ?? ''
  userForm.email = row?.email ?? ''
  userForm.phone = row?.phone ?? ''
  userForm.status = row?.status ?? 'ACTIVE'
  userDialogVisible.value = true
}

async function submitUser() {
  try {
    await saveUser(userForm)
    ElMessage.success('用户已保存')
    userDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '用户保存失败'))
  }
}

function openAssignRoles(row: User) {
  userForm.id = row.id
  userForm.username = row.username
  selectedRoleIds.value = row.roles.map((role) => role.id)
  assignDialogVisible.value = true
}

async function submitUserRoles() {
  try {
    await updateUserRoles(userForm.id, selectedRoleIds.value)
    ElMessage.success('角色已分配')
    assignDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '角色分配失败'))
  }
}

function openRole(row?: Role) {
  roleForm.id = row?.id ?? ''
  roleForm.code = row?.code ?? ''
  roleForm.name = row?.name ?? ''
  roleForm.enabled = row?.enabled ?? true
  roleDialogVisible.value = true
}

async function submitRole() {
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
  roleForm.id = row.id
  roleForm.name = row.name
  selectedPermissionIds.value = row.permissions.map((permission) => permission.id)
  permissionDialogVisible.value = true
}

async function submitRolePermissions() {
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
  try {
    await saveRole({ ...row, enabled: !row.enabled })
    ElMessage.success(row.enabled ? '角色已停用' : '角色已启用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '角色状态更新失败'))
  }
}

onMounted(refresh)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <p>账号、角色、权限和访问范围管理。</p>
      </div>
    </div>

    <div v-loading="loading" class="surface surface-pad">
      <el-tabs>
        <el-tab-pane label="用户">
          <div class="toolbar-row">
            <el-input v-model="userKeyword" placeholder="搜索账号、姓名或角色" :prefix-icon="Search" style="max-width: 320px" />
            <el-button type="primary" :icon="Plus" @click="openUser()">新增用户</el-button>
          </div>

          <el-table :data="filteredUsers" stripe>
            <el-table-column prop="username" label="账号" width="160" />
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
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openUser(row)">编辑</el-button>
                <el-button link type="primary" @click="openAssignRoles(row)">分配角色</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="角色">
          <div class="toolbar-row">
            <el-input v-model="roleKeyword" placeholder="搜索角色编码或名称" :prefix-icon="Search" style="max-width: 320px" />
            <el-button type="primary" :icon="Plus" @click="openRole()">新增角色</el-button>
          </div>

          <el-table :data="filteredRoles" stripe>
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
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRole(row)">编辑</el-button>
                <el-button link type="primary" @click="openRolePermissions(row)">配置权限</el-button>
                <el-button link type="primary" @click="toggleRole(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="权限">
          <el-table :data="permissions" stripe>
            <el-table-column prop="code" label="权限编码" width="180" />
            <el-table-column prop="name" label="权限名称" width="160" />
            <el-table-column prop="resource" label="资源" width="140" />
            <el-table-column prop="action" label="动作" width="140" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="userDialogVisible" title="用户" width="480px">
      <el-form label-width="86px">
        <el-form-item label="账号"><el-input v-model="userForm.username" :disabled="Boolean(userForm.id)" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="userForm.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialogVisible = false">取消</el-button><el-button type="primary" @click="submitUser">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" title="分配角色" width="460px">
      <el-select v-model="selectedRoleIds" multiple style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
      </el-select>
      <template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button type="primary" @click="submitUserRoles">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="角色" width="460px">
      <el-form label-width="86px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.code" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="roleForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="roleDialogVisible = false">取消</el-button><el-button type="primary" @click="submitRole">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionDialogVisible" title="配置权限" width="620px">
      <el-checkbox-group v-model="selectedPermissionIds" class="permission-checks">
        <el-checkbox v-for="permission in permissions" :key="permission.id" :label="permission.id">
          {{ permission.name }}（{{ permission.code }}）
        </el-checkbox>
      </el-checkbox-group>
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
</style>
