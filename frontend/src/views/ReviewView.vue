<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'
import { reviewStateOf } from '../utils/review'

const router = useRouter()
const records = ref([])
const balances = ref([])
const loading = ref(false)
const onlyPending = ref(true)
const filters = reactive({ balanceId: null, recordNo: '' })
const dateRange = ref([])
let refreshTimer = null

const canReview = computed(() => ['REVIEWER', 'ADMIN'].includes(auth.user?.role))
const pendingCount = computed(() => records.value.filter((row) => row.status === 'SUBMITTED').length)

onMounted(async () => {
  await Promise.all([loadBalances(), load()])
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') load(true)
  }, 4000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

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
    balanceId: filters.balanceId || undefined,
    recordNo: filters.recordNo.trim() || undefined,
    status: onlyPending.value ? 'SUBMITTED' : undefined,
    dateFrom: from || undefined,
    dateTo: to || undefined
  }
}

async function load(silent = false) {
  if (silent && loading.value) return
  if (!silent) loading.value = true
  try {
    records.value = await http.get('/calibrations', { params: queryParams() })
  } catch (err) {
    if (!silent) ElMessage.error(err.message)
  } finally {
    if (!silent) loading.value = false
  }
}

function reset() {
  filters.balanceId = null
  filters.recordNo = ''
  dateRange.value = []
  onlyPending.value = true
  load()
}

function isCancel(error) {
  return error === 'cancel' || error === 'close'
}

async function submitReview(row, result, comment) {
  try {
    const record = await http.post(`/calibrations/${row.id}/review`, { result, comment })
    ElMessage.success(result === 'APPROVED'
      ? `${record.recordNo} 已复核通过，记录已锁定`
      : `${record.recordNo} 已退回检验人员修改`)
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function approve(row) {
  try {
    const { value } = await ElMessageBox.prompt('复核意见（可不填）', `通过 ${row.recordNo}`, {
      inputPlaceholder: '例如：示值稳定，与砝码组配置一致',
      inputType: 'textarea',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
    await submitReview(row, 'APPROVED', value)
  } catch (err) {
    if (!isCancel(err)) ElMessage.error(err.message || '操作未完成')
  }
}

async function reject(row) {
  try {
    const { value } = await ElMessageBox.prompt('退回必须说明原因，检验人员会据此修改', `退回 ${row.recordNo}`, {
      inputPlaceholder: '例如：第 5 次称量示值超差，请确认砝码放置',
      inputType: 'textarea',
      inputValidator: (text) => (text && text.trim() ? true : '退回必须填写复核意见'),
      confirmButtonText: '确认退回',
      cancelButtonText: '取消'
    })
    await submitReview(row, 'REJECTED', value)
  } catch (err) {
    if (!isCancel(err)) ElMessage.error(err.message || '操作未完成')
  }
}

async function reopen(row) {
  try {
    await ElMessageBox.confirm(
      `解锁后 ${row.recordNo} 回到草稿，检验人员可以修改并重新提交。确定解锁吗？`,
      '解锁记录',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.post(`/calibrations/${row.id}/reopen`)
    ElMessage.success('已解锁，记录回到草稿')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function open(row) {
  router.push({ path: `/calibrations/${row.id}`, query: { returnTo: '/reviews' } })
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
      <h1 class="page-title">复核工作台</h1>
      <div class="summary">当前列表待复核 <b>{{ pendingCount }}</b> 条</div>
    </div>

    <div class="card">
      <h2>
        复核列表
        <span class="hint" v-if="!canReview">当前账号只能查看，复核操作需复核确认人员</span>
      </h2>
      <div class="filters">
        <el-input
          v-model="filters.recordNo"
          placeholder="按记录编号搜索"
          clearable
          style="width: 190px"
          @keyup.enter="load"
        />
        <el-select v-model="filters.balanceId" placeholder="全部天平" clearable style="width: 130px">
          <el-option v-for="balance in balances" :key="balance.id" :label="balance.code" :value="balance.id" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 230px"
        />
        <el-checkbox v-model="onlyPending" @change="load">只看待复核</el-checkbox>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small" empty-text="当前没有待复核记录">
        <el-table-column label="记录" min-width="190">
          <template #default="{ row }">
            <div class="record-cell">
              <strong>{{ row.recordNo }}</strong>
              <small>{{ row.balanceCode }} · {{ row.balanceUniqueNo }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="inspectorName" label="检验人员" width="90" />
        <el-table-column label="提交时间" width="130">
          <template #default="{ row }">
            <div class="datetime-cell">
              <span>{{ datePart(row.submittedAt || row.calibrationTime) }}</span>
              <small>{{ timePart(row.submittedAt || row.calibrationTime) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结论" width="75" align="center">
          <template #default="{ row }">
            <el-tag :type="row.externalResult === '是' ? 'success' : 'danger'" size="small">
              {{ row.externalResult === '是' ? '通过' : '不通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
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
        <el-table-column label="操作" min-width="190" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">查看</el-button>
            <template v-if="canReview">
              <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click="approve(row)">通过</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click="reject(row)">退回</el-button>
              <el-button v-if="row.status === 'REVIEWED'" link type="warning" @click="reopen(row)">解锁</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="note" style="margin-top: 10px">
        通过后记录锁定为“已复核”，不能再修改；退回则回到草稿，复核意见保留在记录详情中。
        解锁只对已复核记录开放，解锁、复核与退回都会写入审计日志。
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head .page-title { margin: 0; }
.summary { font-size: 13px; color: #5c6b87; }
.summary b { color: #1f4e8c; font-size: 16px; }
.filters { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.record-cell strong, .record-cell small { display: block; }
.record-cell small { color: #7a889f; font-size: 11px; margin-top: 3px; }
.datetime-cell { line-height: 1.35; white-space: nowrap; }
.datetime-cell span, .datetime-cell small { display: block; }
.datetime-cell small { color: #7a889f; font-size: 11px; }
</style>
