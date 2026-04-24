<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { getCategories, getTags, saveCategory, saveTag, type Category, type Tag } from '../api/taxonomy'

const loading = ref(false)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const activeCategoryId = ref('')
const keyword = ref('')
const categoryDialogVisible = ref(false)
const tagDialogVisible = ref(false)
const categoryForm = reactive({ id: '', code: '', name: '', sortOrder: 0, enabled: true })
const tagForm = reactive({ id: '', name: '', sortOrder: 0, enabled: true })

const activeCategory = computed(() => categories.value.find((category) => category.id === activeCategoryId.value))
const tagRows = computed(() => {
  const query = keyword.value.trim()
  return tags.value.filter((tag) => !query || tag.name.includes(query))
})

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

async function loadCategories() {
  categories.value = await getCategories()
  if (!activeCategoryId.value && categories.value.length > 0) {
    activeCategoryId.value = categories.value[0].id
  }
}

async function loadTags() {
  tags.value = activeCategoryId.value ? await getTags(activeCategoryId.value) : []
}

async function refresh() {
  loading.value = true
  try {
    await loadCategories()
    await loadTags()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类标签加载失败'))
  } finally {
    loading.value = false
  }
}

async function selectCategory(id: string) {
  activeCategoryId.value = id
  keyword.value = ''
  await loadTags()
}

function openCategory(row?: Category) {
  categoryForm.id = row?.id ?? ''
  categoryForm.code = row?.code ?? ''
  categoryForm.name = row?.name ?? ''
  categoryForm.sortOrder = row?.sortOrder ?? 0
  categoryForm.enabled = row?.enabled ?? true
  categoryDialogVisible.value = true
}

async function submitCategory() {
  try {
    const saved = await saveCategory(categoryForm)
    activeCategoryId.value = saved.id
    ElMessage.success('分类已保存')
    categoryDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '分类保存失败'))
  }
}

function openTag(row?: Tag) {
  tagForm.id = row?.id ?? ''
  tagForm.name = row?.name ?? ''
  tagForm.sortOrder = row?.sortOrder ?? 0
  tagForm.enabled = row?.enabled ?? true
  tagDialogVisible.value = true
}

async function submitTag() {
  if (!activeCategoryId.value) return
  try {
    await saveTag(activeCategoryId.value, tagForm)
    ElMessage.success('标签已保存')
    tagDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签保存失败'))
  }
}

async function disableTag(row: Tag) {
  try {
    await saveTag(row.categoryId, { ...row, enabled: !row.enabled })
    ElMessage.success(row.enabled ? '标签已停用' : '标签已启用')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标签状态更新失败'))
  }
}

onMounted(refresh)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>分类标签</h1>
        <p>维护图片分类及其下的标签，标签名称只在所属分类内保持唯一。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCategory()">新增分类</el-button>
    </div>

    <div v-loading="loading" class="surface taxonomy-workspace">
      <aside class="taxonomy-categories">
        <div class="taxonomy-pane-head">
          <span>分类</span>
          <el-button link type="primary" @click="openCategory()">新增</el-button>
        </div>
        <button
          v-for="category in categories"
          :key="category.id"
          class="category-option"
          :class="{ active: category.id === activeCategoryId }"
          type="button"
          @click="selectCategory(category.id)"
        >
          <span>
            <strong>{{ category.name }}</strong>
            <small>{{ category.code }}</small>
          </span>
          <em>{{ category.tagCount }}</em>
        </button>
      </aside>

      <div class="taxonomy-tags">
        <div class="taxonomy-detail-head">
          <div>
            <h2>{{ activeCategory?.name ?? '请选择分类' }}</h2>
            <p>{{ tags.length }} 个标签</p>
          </div>
          <div>
            <el-button v-if="activeCategory" @click="openCategory(activeCategory)">编辑分类</el-button>
            <el-button type="primary" :icon="Plus" :disabled="!activeCategory" @click="openTag()">新增标签</el-button>
          </div>
        </div>

        <div class="toolbar-row">
          <el-input v-model="keyword" placeholder="搜索标签名称" :prefix-icon="Search" style="max-width: 320px" />
          <el-tag v-if="activeCategory" :type="activeCategory.enabled ? 'success' : 'info'">
            {{ activeCategory.enabled ? '启用' : '停用' }}
          </el-tag>
        </div>

        <el-table :data="tagRows" stripe height="520">
          <el-table-column prop="name" label="标签名称" min-width="180" />
          <el-table-column prop="sortOrder" label="排序" width="100" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTag(row)">编辑</el-button>
              <el-button link type="primary" @click="disableTag(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

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

    <el-dialog v-model="tagDialogVisible" title="标签" width="420px">
      <el-form label-width="80px">
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
