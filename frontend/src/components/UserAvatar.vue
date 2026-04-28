<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { http } from '../api/http'

const props = withDefaults(defineProps<{
  name?: string | null
  avatarUrl?: string | null
  size?: number
}>(), {
  name: '',
  avatarUrl: null,
  size: 32,
})

const objectUrl = ref('')
const loadFailed = ref(false)

const initials = computed(() => {
  const text = (props.name ?? '').trim()
  if (!text) return '用'
  const chars = Array.from(text)
  return chars[0] ?? '用'
})

const avatarStyle = computed(() => ({
  '--avatar-size': `${props.size}px`,
  '--avatar-bg': fallbackColor(props.name ?? ''),
}))

watch(() => props.avatarUrl, loadAvatar, { immediate: true })

onBeforeUnmount(revokeObjectUrl)

async function loadAvatar() {
  revokeObjectUrl()
  loadFailed.value = false
  const url = normalizeAvatarUrl(props.avatarUrl)
  if (!url) return
  try {
    const response = await http.get<Blob>(url, { responseType: 'blob' })
    objectUrl.value = URL.createObjectURL(response.data)
  } catch {
    loadFailed.value = true
  }
}

function revokeObjectUrl() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

function normalizeAvatarUrl(url?: string | null) {
  if (!url) return ''
  return url.startsWith('/api/') ? url.slice(4) : url
}

function fallbackColor(name: string) {
  const colors = ['#2563eb', '#059669', '#7c3aed', '#dc2626', '#0891b2', '#ca8a04', '#be185d', '#475569']
  const source = name || 'user'
  let hash = 0
  for (const char of source) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  return colors[hash % colors.length]
}
</script>

<template>
  <span class="user-avatar" :style="avatarStyle" aria-hidden="true">
    <img v-if="objectUrl && !loadFailed" :src="objectUrl" :alt="`${name || '用户'}头像`" @error="loadFailed = true" />
    <span v-else>{{ initials }}</span>
  </span>
</template>

<style scoped>
.user-avatar {
  align-items: center;
  background: var(--avatar-bg);
  border-radius: 999px;
  color: #ffffff;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: max(12px, calc(var(--avatar-size) * 0.42));
  font-weight: 700;
  height: var(--avatar-size);
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  width: var(--avatar-size);
}

.user-avatar img {
  display: block;
  height: 100%;
  object-fit: cover;
  width: 100%;
}
</style>
