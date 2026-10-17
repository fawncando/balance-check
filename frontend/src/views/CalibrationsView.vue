<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'
import { reviewResultText, reviewStateOf } from '../utils/review'

const router = useRouter()
const records = ref([])
const balances = ref([])
const loading = ref(false)
const filters = reactive({ recordNo: '', balanceId: null, status: '' })
const dateRange = ref([])

const isInspector = computed(() => auth.user?.role === 'INSPECTOR')

onMounted(async () => {
  await Promise.all([loadBalances(), load()])
})

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
    dateTo: to || undefined
  }
}

async function load() {
  loading.value = true
  try {
    records.value = await http.get('/calibrations', { params: queryParams() })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.recordNo = ''
  filters.balanceId = null
  filters.status = ''
  dateRange.value = []
  load()
}

function open(row) {
  router.push(`/calibrations/${row.id}`)
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
          @keyup.enter="load"
        />
        <el-select v-model="filters.balanceId" placeholder="全部天平" clearable style="width: 130px">
          <el-option v-for="balance in balances" :key="balance.id" :label="balance.code" :value="balance.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待复核" value="SUBMITTED" />
          <el-option label="已复核" value="REVIEWED" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 230px"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="card">
      <h2>记录列表<span class="hint">共 {{ records.length }} 条 · 按校验时间倒序</span></h2>
      <el-table :data="records" v-loading="loading" size="small" empty-text="没有符合条件的记录">
        <el-table-column prop="recordNo" label="记录编号" width="175" />
        <el-table-column prop="balanceCode" label="天平" width="80" />
        <el-table-column prop="department" label="科室" width="80" />
        <el-table-column prop="calibrationTime" label="校验时间" width="160" />
        <el-table-column prop="inspectorName" label="检验人员" width="90" />
        <el-table-column label="结论" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.externalResult === '是' ? 'success' : 'danger'" size="small">
              {{ row.externalResult === '是' ? '通过' : '不通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
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
        <el-table-column label="复核结论" width="95" align="center">
          <template #default="{ row }">{{ reviewResultText(row.reviewResult) }}</template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="复核人" width="90" />
        <el-table-column prop="reviewedAt" label="复核时间" width="160" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head .page-title { margin: 0; }
.filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
</style>
