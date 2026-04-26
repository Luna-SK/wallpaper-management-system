<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import {
  getCategories,
  getTagGroups,
  getTags,
  purgeCategory,
  purgeTag,
  purgeTagGroup,
  restoreCategory,
  restoreTag,
  restoreTagGroup,
  saveCategory,
  saveTag,
  saveTagGroup,
  type Category,
  type ReferenceImpact,
  type Tag,
  type TagGroup,
} from '../api/taxonomy'
import { useDialogEnterSubmit } from '../utils/dialogEnterSubmit'

interface ApiError<T = unknown> {
  code?: string
  message?: string
  data?: T
}

const loading = ref(false)
const activeTab = ref<'categories' | 'tags'>('categories')
const categoryScope = ref<'ACTIVE' | 'DISABLED'>('ACTIVE')
const groupScope = ref<'ACTIVE' | 'DISABLED'>('ACTIVE')
const tagScope = ref<'ACTIVE' | 'DISABLED'>('ACTIVE')
const categories = ref<Category[]>([])
const tagGroups = ref<TagGroup[]>([])
const tags = ref<Tag[]>([])
const activeGroupId = ref('')
const categoryKeyword = ref('')
const tagKeyword = ref('')
const categoryDialogVisible = ref(false)
const groupDialogVisible = ref(false)
const tagDialogVisible = ref(false)
const categoryForm = reactive({ id: '', code: '', name: '', sortOrder: 0, enabled: true })
const groupForm = reactive({ id: '', code: '', name: '', sortOrder: 0, enabled: true })
const tagForm = reactive({ id: '', groupId: '', name: '', sortOrder: 0, enabled: true })

const categoryRows = computed(() => {
  const query = categoryKeyword.value.trim()
  const enabled = categoryScope.value === 'ACTIVE'
  return categories.value.filter((category) => category.enabled === enabled && (!query || category.name.includes(query) || category.code.includes(query)))
})

const groupRows = computed(() => {
  const enabled = groupScope.value === 'ACTIVE'
  return tagGroups.value.filter((group) => group.enabled === enabled)
})

const activeGroup = computed(() => tagGroups.value.find((group) => group.id === activeGroupId.value) ?? null)

const tagRows = computed(() => {
  if (!activeGroupId.value) {
    return []
  }
  const query = tagKeyword.value.trim()
  const enabled = tagScope.value === 'ACTIVE'
  return tags.value.filter((tag) => {
    const matchesGroup = tag.groupId === activeGroupId.value
    const matchesScope = tag.enabled === enabled
    const matchesQuery = !query || tag.name.includes(query) || (tag.groupName ?? '').includes(query)
    return matchesGroup && matchesScope && matchesQuery
  })
})

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<ApiError>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function conflictImpact(error: unknown) {
  if (!isAxiosError<ApiError<ReferenceImpact>>(error) || error.response?.status !== 409) {
    return null
  }
  return error.response.data.data ?? null
}

function impactText(impact: ReferenceImpact) {
  const parts = [
    impact.imageCount ? `${impact.imageCount} 张图片` : '',
    impact.uploadBatchCount ? `${impact.uploadBatchCount} 个上传会话` : '',
    impact.tagCount ? `${impact.tagCount} 个标签` : '',
  ].filter(Boolean)
  return parts.length ? parts.join('、') : '无引用'
}

async function loadAll() {
  const [categoryData, groupData, tagData] = await Promise.all([getCategories(), getTagGroups(), getTags()])
  categories.value = categoryData
  tagGroups.value = groupData
  tags.value = tagData
  syncActiveGroup()
}

async function refresh() {
  loading.value = true
  try {
    await loadAll()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类标签加载失败'))
  } finally {
    loading.value = false
  }
}

function syncActiveGroup() {
  if (activeGroupId.value && groupRows.value.some((group) => group.id === activeGroupId.value)) {
    return
  }
  activeGroupId.value = groupRows.value[0]?.id ?? ''
}

function selectGroup(id: string) {
  activeGroupId.value = id
  tagKeyword.value = ''
}

function openCategory(row?: Category) {
  categoryForm.id = row?.id ?? ''
  categoryForm.code = row?.code ?? ''
  categoryForm.name = row?.name ?? ''
  categoryForm.sortOrder = row?.sortOrder ?? 0
  categoryForm.enabled = row?.enabled ?? categoryScope.value === 'ACTIVE'
  categoryDialogVisible.value = true
}

async function submitCategory() {
  try {
    await saveCategory(categoryForm)
    ElMessage.success('分类已保存')
    categoryDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类保存失败'))
  }
}

function openGroup(row?: TagGroup) {
  groupForm.id = row?.id ?? ''
  groupForm.code = row?.code ?? ''
  groupForm.name = row?.name ?? ''
  groupForm.sortOrder = row?.sortOrder ?? 0
  groupForm.enabled = row?.enabled ?? groupScope.value === 'ACTIVE'
  groupDialogVisible.value = true
}

async function submitGroup() {
  try {
    const saved = await saveTagGroup(groupForm)
    activeGroupId.value = saved.id
    ElMessage.success('标签组已保存')
    groupDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签组保存失败'))
  }
}

function openTag(row?: Tag) {
  tagForm.id = row?.id ?? ''
  tagForm.groupId = row?.groupId ?? activeGroupId.value
  tagForm.name = row?.name ?? ''
  tagForm.sortOrder = row?.sortOrder ?? 0
  tagForm.enabled = row?.enabled ?? tagScope.value === 'ACTIVE'
  tagDialogVisible.value = true
}

async function submitTag() {
  try {
    await saveTag(tagForm)
    ElMessage.success('标签已保存')
    tagDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签保存失败'))
  }
}

async function disableCategory(row: Category) {
  try {
    await saveCategory({ ...row, enabled: false })
    ElMessage.success('分类已停用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类状态更新失败'))
  }
}

async function disableGroup(row: TagGroup) {
  try {
    await saveTagGroup({ ...row, enabled: false })
    ElMessage.success('标签组已停用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签组状态更新失败'))
  }
}

async function disableTag(row: Tag) {
  try {
    await saveTag({ ...row, enabled: false })
    ElMessage.success('标签已停用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签状态更新失败'))
  }
}

async function restoreCategoryRow(row: Category) {
  try {
    await restoreCategory(row.id)
    ElMessage.success('分类已恢复')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类恢复失败'))
  }
}

async function restoreGroupRow(row: TagGroup) {
  try {
    await restoreTagGroup(row.id)
    ElMessage.success('标签组已恢复')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签组恢复失败'))
  }
}

async function restoreTagRow(row: Tag) {
  try {
    await restoreTag(row.id)
    ElMessage.success('标签已恢复')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签恢复失败'))
  }
}

async function restoreActiveGroup() {
  if (activeGroup.value) {
    await restoreGroupRow(activeGroup.value)
  }
}

async function purgeActiveGroup() {
  const group = activeGroup.value
  if (group) {
    await purgeWithConfirm(`标签组「${group.name}」`, (force) => purgeTagGroup(group.id, force))
  }
}

async function purgeWithConfirm(name: string, purge: (force?: boolean) => Promise<ReferenceImpact>) {
  try {
    await purge(false)
    ElMessage.success(`${name}已彻底删除`)
    await refresh()
  } catch (error) {
    const impact = conflictImpact(error)
    if (!impact) {
      ElMessage.error(errorMessage(error, `${name}彻底删除失败`))
      return
    }
    try {
      await ElMessageBox.confirm(
        `${name}仍关联 ${impactText(impact)}。继续后会自动解除这些关联并彻底删除，操作不可恢复。`,
        '危险操作确认',
        {
          confirmButtonText: '解除引用并彻底删除',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
    await purge(true)
    ElMessage.success(`${name}已彻底删除`)
    await refresh()
  }
}

useDialogEnterSubmit(categoryDialogVisible, submitCategory)
useDialogEnterSubmit(groupDialogVisible, submitGroup)
useDialogEnterSubmit(tagDialogVisible, submitTag)

function handleGroupScopeChange() {
  syncActiveGroup()
}

onMounted(refresh)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <p>维护图片分类、标签组和标签。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="surface surface-pad">
      <el-tab-pane label="分类" name="categories">
        <div class="toolbar-row">
          <el-radio-group v-model="categoryScope">
            <el-radio-button label="ACTIVE">在用</el-radio-button>
            <el-radio-button label="DISABLED">已停用</el-radio-button>
          </el-radio-group>
          <el-input v-model="categoryKeyword" placeholder="搜索分类" :prefix-icon="Search" clearable style="max-width: 320px" />
          <el-button type="primary" :icon="Plus" @click="openCategory()">新增分类</el-button>
        </div>
        <el-table v-loading="loading" :data="categoryRows" stripe height="560">
          <el-table-column prop="name" label="分类名称" min-width="180" />
          <el-table-column prop="code" label="编码" min-width="160" />
          <el-table-column prop="imageCount" label="图片数" width="110" />
          <el-table-column prop="sortOrder" label="排序" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <template v-if="row.enabled">
                <el-button link type="primary" @click="openCategory(row)">编辑</el-button>
                <el-button link type="danger" @click="disableCategory(row)">停用</el-button>
              </template>
              <template v-else>
                <el-button link type="primary" @click="openCategory(row)">编辑</el-button>
                <el-button link type="primary" @click="restoreCategoryRow(row)">恢复</el-button>
                <el-button link type="danger" @click="purgeWithConfirm(`分类「${row.name}」`, (force) => purgeCategory(row.id, force))">彻底删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="标签" name="tags">
        <div v-loading="loading" class="taxonomy-workspace">
          <aside class="taxonomy-categories">
            <div class="taxonomy-pane-head">
              <span>标签组</span>
              <el-button link type="primary" @click="openGroup()">新增</el-button>
            </div>
            <el-radio-group v-model="groupScope" class="scope-toggle" @change="handleGroupScopeChange">
              <el-radio-button label="ACTIVE">在用</el-radio-button>
              <el-radio-button label="DISABLED">已停用</el-radio-button>
            </el-radio-group>
            <button
              v-for="group in groupRows"
              :key="group.id"
              class="category-option"
              :class="{ active: group.id === activeGroupId }"
              type="button"
              @click="selectGroup(group.id)"
            >
              <span>
                <strong>{{ group.name }}</strong>
                <small>{{ group.code }}</small>
              </span>
              <em>{{ group.tagCount }}</em>
            </button>
          </aside>

          <div class="taxonomy-tags">
            <div class="taxonomy-detail-head">
              <div>
                <h2>{{ activeGroup?.name ?? '请选择标签组' }}</h2>
                <p>{{ tagRows.length }} 个标签</p>
              </div>
              <div>
                <el-button v-if="activeGroup" @click="openGroup(activeGroup)">编辑标签组</el-button>
                <el-button v-if="activeGroup?.enabled" type="primary" :icon="Plus" @click="openTag()">新增标签</el-button>
                <el-button v-if="activeGroup && activeGroup.enabled" type="danger" plain @click="disableGroup(activeGroup)">停用标签组</el-button>
                <template v-if="activeGroup && !activeGroup.enabled">
                  <el-button type="primary" plain @click="restoreActiveGroup">恢复标签组</el-button>
                  <el-button type="danger" @click="purgeActiveGroup">彻底删除</el-button>
                </template>
              </div>
            </div>

            <div class="toolbar-row">
              <el-radio-group v-model="tagScope">
                <el-radio-button label="ACTIVE">在用</el-radio-button>
                <el-radio-button label="DISABLED">已停用</el-radio-button>
              </el-radio-group>
              <el-input v-model="tagKeyword" placeholder="搜索标签" :prefix-icon="Search" clearable style="max-width: 320px" />
            </div>

            <el-table :data="tagRows" stripe height="480">
              <el-table-column prop="name" label="标签名称" min-width="180" />
              <el-table-column prop="groupName" label="标签组" min-width="140" />
              <el-table-column prop="sortOrder" label="排序" width="100" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <template v-if="row.enabled">
                    <el-button link type="primary" @click="openTag(row)">编辑</el-button>
                    <el-button link type="danger" @click="disableTag(row)">停用</el-button>
                  </template>
                  <template v-else>
                    <el-button link type="primary" @click="openTag(row)">编辑</el-button>
                    <el-button link type="primary" @click="restoreTagRow(row)">恢复</el-button>
                    <el-button link type="danger" @click="purgeWithConfirm(`标签「${row.name}」`, (force) => purgeTag(row.id, force))">彻底删除</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="categoryDialogVisible" title="分类" width="460px">
      <el-form label-width="90px">
        <el-form-item label="分类编码"><el-input v-model="categoryForm.code" /></el-form-item>
        <el-form-item label="分类名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="categoryForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDialogVisible" title="标签组" width="460px">
      <el-form label-width="90px">
        <el-form-item label="标签组编码"><el-input v-model="groupForm.code" /></el-form-item>
        <el-form-item label="标签组名称"><el-input v-model="groupForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortOrder" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="groupForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagDialogVisible" title="标签" width="460px">
      <el-form label-width="90px">
        <el-form-item label="所属标签组">
          <el-select v-model="tagForm.groupId" placeholder="选择标签组">
            <el-option v-for="group in tagGroups" :key="group.id" :label="group.name" :value="group.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称"><el-input v-model="tagForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="tagForm.sortOrder" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="tagForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTag">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.scope-toggle {
  width: 100%;
  margin-bottom: 12px;
}
</style>
