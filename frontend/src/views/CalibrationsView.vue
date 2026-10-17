<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const router = useRouter()
const records = ref([])
const loading = ref(false)

const statusMap = {
  DRAFT: { text: '草稿', type: 'info' },
  SUBMITTED: { text: '待复核', type: 'warning' },
  REVIEWED: { text: '已复核', type: 'success' }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    records.value = await http.get('/calibrations')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function open(row) {
  router.push(`/calibrations/${row.id}`)
}
</script>

<template>
  <div>
    <div class="head">
      <h1 class="page-title">校验记录</h1>
      <el-button type="primary" @click="router.push('/calibrations/new')">新建校验记录</el-button>
    </div>
    <div class="card">
      <h2>全部记录<span class="hint">按创建时间倒序，不同天平、不同检验人员的记录都可查</span></h2>
      <el-table :data="records" v-loading="loading" size="small" empty-text="还没有校验记录">
        <el-table-column prop="recordNo" label="记录编号" width="190" />
        <el-table-column prop="balanceCode" label="天平" width="90" />
        <el-table-column prop="calibrationTime" label="校验时间" width="170" />
        <el-table-column prop="inspectorName" label="检验人员" width="100" />
        <el-table-column label="结论" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.externalResult === '是' ? 'success' : 'danger'" size="small">
              {{ row.externalResult === '是' ? '通过' : '不通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
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
</style>
