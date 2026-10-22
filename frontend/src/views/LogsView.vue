<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const records = ref([])
const users = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ action: '', userId: null })
const dateRange = ref([])

const isAdmin = computed(() => auth.user?.role === 'ADMIN')

const ACTION_LABELS = {
  LOGIN: '登录',
  LOGOUT: '退出登录',
  VIDEO_UPLOAD: '上传视频',
  VIDEO_DELETE: '删除视频',
  CALIBRATION_CREATE: '新建记录',
  CALIBRATION_UPDATE: '修改记录',
  CALIBRATION_SUBMIT: '提交复核',
  CALIBRATION_REVIEW: '复核记录',
  CALIBRATION_REOPEN: '解锁记录',
  BALANCE_CREATE: '新增天平',
  BALANCE_UPDATE: '修改天平',
  TEMPLATE_CREATE: '新增模板',
  TEMPLATE_ENABLE: '启用模板',
  TEMPLATE_DISABLE: '停用模板'
}
const actionOptions = Object.keys(ACTION_LABELS)

onMounted(async () => {
  if (!isAdmin.value) return
  await Promise.all([loadUsers(), load()])
})

async function loadUsers() {
  try {
    users.value = await http.get('/users')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function load() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    const [from, to] = dateRange.value || []
    const data = await http.get('/logs', {
      params: {
        action: filters.action || undefined,
        userId: filters.userId || undefined,
        dateFrom: from || undefined,
        dateTo: to || undefined,
        page: page.value,
        size: size.value
      }
    })
    records.value = data.items || []
    total.value = data.total || 0
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.action = ''
  filters.userId = null
  dateRange.value = []
  page.value = 1
  load()
}

function onPageChange(value) {
  page.value = value
  load()
}

function onSizeChange(value) {
  size.value = value
  page.value = 1
  load()
}

function actionLabel(action) {
  return ACTION_LABELS[action] || action
}
</script>

<template>
  <div>
    <h1 class="page-title">操作日志</h1>

    <div v-if="!isAdmin" class="card">
      <h2>无权限</h2>
      <div class="note">操作日志只对管理员开放。</div>
    </div>

    <template v-else>
      <div class="card">
        <h2>查询条件<span class="hint">共 {{ total }} 条 · 按时间倒序</span></h2>
        <div class="filters">
          <el-select v-model="filters.action" placeholder="全部操作" clearable filterable style="width: 160px">
            <el-option v-for="action in actionOptions" :key="action" :label="actionLabel(action)" :value="action" />
          </el-select>
          <el-select v-model="filters.userId" placeholder="全部操作人" clearable style="width: 150px">
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="`${user.displayName}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 230px"
          />
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <div class="card">
        <el-table :data="records" v-loading="loading" size="small" empty-text="没有符合条件的日志">
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="操作人" width="110">
            <template #default="{ row }">{{ row.userName || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ actionLabel(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetType" label="对象" width="110" />
          <el-table-column prop="targetId" label="对象编号" width="100" />
          <el-table-column prop="detail" label="说明" min-width="260" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="130" />
        </el-table>
        <el-pagination
          class="pager"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[20, 50, 100]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
