<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http, { getPage } from '../api/http'
import { auth } from '../stores/auth'
import { reviewResultText, reviewStateOf } from '../utils/review'

const router = useRouter()
const records = ref([])
const balances = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ recordNo: '', balanceId: null, status: '' })
const dateRange = ref([])
const compact = ref(false)
let refreshTimer = null

const isInspector = computed(() => auth.user?.role === 'INSPECTOR')

onMounted(async () => {
  syncCompact()
  window.addEventListener('resize', syncCompact)
  await Promise.all([loadBalances(), load()])
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') load(true)
  }, 4000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncCompact)
  if (refreshTimer) window.clearInterval(refreshTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

function syncCompact() {
  compact.value = window.innerWidth <= 1200
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') load(true)
}

async function loadBalances() {
  try {
    balances.value = await http.get('/balances')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function queryParams() {
  const [from, to] = dateRange.value || []
  return {
    recordNo: filters.recordNo.trim() || undefined,
    balanceId: filters.balanceId || undefined,
    status: filters.status || undefined,
    dateFrom: from || undefined,
    dateTo: to || undefined,
    page: page.value,
    pageSize: pageSize.value
  }
}

async function load(silent = false) {
  if (silent && loading.value) return
  if (!silent) loading.value = true
  try {
    const result = await getPage('/calibrations', { params: queryParams() })
    records.value = result.items
    total.value = result.total
  } catch (err) {
    if (!silent) ElMessage.error(err.message)
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageSizeChange() {
  page.value = 1
  load()
}

function reset() {
  filters.recordNo = ''
  filters.balanceId = null
  filters.status = ''
  dateRange.value = []
  page.value = 1
  load()
}

function open(row) {
  router.push({ path: `/calibrations/${row.id}`, query: { returnTo: '/records' } })
}

function datePart(value) {
  return value ? String(value).slice(0, 10) : '-'
}

function timePart(value) {
  const time = value ? String(value).split('T')[1] : ''
  return time ? time.slice(0, 8) : ''
}
</script>

<template>
  <div>
    <div class="head">
      <h1 class="page-title">校验记录查询</h1>
      <el-button type="primary" @click="router.push('/calibrations/new')">新建校验记录</el-button>
    </div>

    <div class="card">
      <h2>
        查询条件
        <span class="hint" v-if="isInspector">检验人员只查询到自己创建的记录</span>
      </h2>
      <div class="filters">
        <el-input
          v-model="filters.recordNo"
          placeholder="按记录编号搜索"
          clearable
          style="width: 190px"
          @keyup.enter="search"
        />
        <el-select v-model="filters.balanceId" placeholder="全部天平" clearable style="width: 130px">
          <el-option v-for="balance in balances" :key="balance.id" :label="balance.code" :value="balance.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待复核" value="SUBMITTED" />
          <el-option label="已复核" value="REVIEWED" />
          <el-option label="已作废" value="VOIDED" />
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
      <h2>记录列表<span class="hint">共 {{ total }} 条 · 按校验时间倒序</span></h2>
      <el-table :data="records" v-loading="loading" size="small" empty-text="没有符合条件的记录">
        <el-table-column label="记录编号" width="180">
          <template #default="{ row }">
            <div class="record-no">{{ row.recordNo }}</div>
            <div
              v-if="row.reviewResult === 'REJECTED' && row.reviewComment"
              class="reject-note"
              :title="row.reviewComment"
            >
              退回：{{ row.reviewComment }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="balanceCode" label="天平" width="65" />
        <el-table-column v-if="!compact" prop="department" label="科室" width="70" />
        <el-table-column label="校验时间" width="130">
          <template #default="{ row }">
            <div class="datetime-cell">
              <span>{{ datePart(row.calibrationTime) }}</span>
              <small>{{ timePart(row.calibrationTime) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="inspectorName" label="检验人员" width="80" />
        <el-table-column label="结论" width="75" align="center">
          <template #default="{ row }">
            <el-tag :type="row.externalResult === '是' ? 'success' : 'danger'" size="small">
              {{ row.externalResult === '是' ? '通过' : '不通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="reviewStateOf(row.status, row.reviewResult).type"
              :effect="row.status === 'SUBMITTED' ? 'dark' : 'light'"
              size="small"
            >
              {{ reviewStateOf(row.status, row.reviewResult).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="复核结论" width="80" align="center">
          <template #default="{ row }">{{ reviewResultText(row.reviewResult) }}</template>
        </el-table-column>
        <el-table-column v-if="!compact" prop="reviewerName" label="复核人" width="80" />
        <el-table-column v-if="!compact" label="复核时间" width="130">
          <template #default="{ row }">
            <div class="datetime-cell">
              <span>{{ datePart(row.reviewedAt) }}</span>
              <small>{{ timePart(row.reviewedAt) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="65" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load()"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head .page-title { margin: 0; }
.filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.record-no { font-weight: 600; }
.datetime-cell { line-height: 1.35; white-space: nowrap; }
.datetime-cell span, .datetime-cell small { display: block; }
.datetime-cell small { color: #7a889f; font-size: 11px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.reject-note {
  margin-top: 4px;
  color: #b03a2e;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
