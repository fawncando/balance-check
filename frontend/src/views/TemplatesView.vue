<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ name: '', version: '', note: '', enabled: false })

const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const names = computed(() => [...new Set(rows.value.map((row) => row.name))])

onMounted(load)

async function load() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    rows.value = await http.get('/templates')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function openCreate(presetName = '') {
  form.name = presetName
  form.version = ''
  form.note = ''
  form.enabled = false
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim() || !form.version.trim()) {
    ElMessage.warning('请填写模板名称与版本号')
    return
  }
  saving.value = true
  try {
    await http.post('/templates', {
      name: form.name.trim(),
      version: form.version.trim(),
      note: form.note.trim(),
      enabled: form.enabled
    })
    ElMessage.success('模板版本已保存')
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const enable = !row.enabled
  if (enable) {
    try {
      await ElMessageBox.confirm('启用该版本后，同名模板的其他版本会自动停用。确定启用吗？', '启用模板版本', { type: 'warning' })
    } catch {
      return
    }
  }
  try {
    await http.put(`/templates/${row.id}/enabled?enabled=${enable}`)
    ElMessage.success(enable ? '已启用' : '已停用')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}
</script>

<template>
  <div>
    <div class="head">
      <h1 class="page-title">记录表模板</h1>
      <el-button v-if="isAdmin" type="primary" @click="openCreate('')">新增版本</el-button>
    </div>

    <div v-if="!isAdmin" class="card">
      <h2>无权限</h2>
      <div class="note">记录表模板只对管理员开放。</div>
    </div>

    <div v-else class="card">
      <h2>模板版本<span class="hint">同名模板同一时间只启用一个版本</span></h2>
      <el-table :data="rows" v-loading="loading" size="small" empty-text="还没有模板版本">
        <el-table-column prop="name" label="模板名称" min-width="180" />
        <el-table-column prop="version" label="版本" width="110" />
        <el-table-column prop="note" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用中' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openCreate(row.name)">新版本</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="note" style="margin-top: 10px">
        记录表导出仍使用内置 A4 版式；启用版本记录在这里，供归档说明与后续版本迭代使用。
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="新增模板版本" width="520px">
      <el-form label-width="90px">
        <el-form-item label="模板名称">
          <el-select
            v-model="form.name"
            filterable
            allow-create
            default-first-option
            placeholder="选择已有模板或输入新名称"
            style="width: 100%"
          >
            <el-option v-for="name in names" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="form.version" placeholder="如 V2" maxlength="20" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="本次版本调整的说明" />
        </el-form-item>
        <el-form-item label="立即启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head .page-title { margin: 0; }
</style>
