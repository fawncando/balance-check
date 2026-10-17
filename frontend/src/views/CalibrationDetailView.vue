<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const record = ref(null)
const loading = ref(false)

const statusMap = {
  DRAFT: { text: '草稿', type: 'info' },
  SUBMITTED: { text: '待复核', type: 'warning' },
  REVIEWED: { text: '已复核', type: 'success' }
}

const canEdit = computed(() =>
  record.value?.status === 'DRAFT' &&
  (record.value?.inspectorName === auth.user?.displayName || auth.user?.role === 'ADMIN')
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    record.value = await http.get(`/calibrations/${route.params.id}`)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function submit() {
  await ElMessageBox.confirm('提交后记录不能再编辑，确定提交吗？', '提交复核', { type: 'warning' })
  try {
    record.value = await http.post(`/calibrations/${record.value.id}/submit`)
    ElMessage.success('已提交复核')
  } catch (err) {
    ElMessage.error(err.message)
  }
}
</script>

<template>
  <div v-loading="loading">
    <div class="head">
      <h1 class="page-title">校验记录详情</h1>
      <div class="actions">
        <el-button v-if="canEdit" @click="router.push(`/calibrations/${record.id}/edit`)">编辑</el-button>
        <el-button v-if="canEdit" type="primary" @click="submit">提交复核</el-button>
        <el-button link @click="router.push('/records')">返回列表</el-button>
      </div>
    </div>

    <div v-if="record">
      <div class="card">
        <h2>
          {{ record.recordNo }}
          <el-tag :type="statusMap[record.status]?.type" size="small" style="margin-left: 8px">
            {{ statusMap[record.status]?.text }}
          </el-tag>
        </h2>
        <div class="kv">
          <div><span class="k">天平：</span>{{ record.balanceCode }}</div>
          <div><span class="k">校验时间：</span>{{ record.calibrationTime }}</div>
          <div><span class="k">检验人员：</span>{{ record.inspectorName }}</div>
          <div><span class="k">外部校正：</span>
            <el-tag :type="record.externalResult === '是' ? 'success' : 'danger'" size="small">
              {{ record.externalResult === '是' ? '通过' : '不通过' }}
            </el-tag>
          </div>
          <div><span class="k">清洁：</span>{{ record.cleanOk ? '是' : '否' }}</div>
          <div><span class="k">水平调节：</span>{{ record.levelOk ? '是' : '否' }}</div>
          <div><span class="k">内部校正：</span>{{ record.internalCalibrationResult }}</div>
          <div><span class="k">备注：</span>{{ record.remark }}</div>
        </div>
      </div>

      <div class="card">
        <h2>九次称量明细</h2>
        <el-table :data="record.measurements" size="small">
          <el-table-column label="量程" width="80" align="center">
            <template #default="{ row }">R{{ row.rangeSeq }}</template>
          </el-table-column>
          <el-table-column label="砝码" width="90" align="right">
            <template #default="{ row }">{{ row.nominalG }} g</template>
          </el-table-column>
          <el-table-column label="次序" width="70" align="center">
            <template #default="{ row }">{{ row.measureSeq }}</template>
          </el-table-column>
          <el-table-column label="示值" width="130" align="right">
            <template #default="{ row }">{{ row.displayValue }} {{ row.displayUnit }}</template>
          </el-table-column>
          <el-table-column label="换算值（g）" width="130" align="right">
            <template #default="{ row }">{{ row.valueG }}</template>
          </el-table-column>
          <el-table-column label="限度（g）" min-width="190">
            <template #default="{ row }">{{ row.lowerG }} ~ {{ row.upperG }}</template>
          </el-table-column>
          <el-table-column prop="measuredAt" label="测量时间" width="170" />
          <el-table-column label="判定" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.qualified ? 'success' : 'danger'" size="small">
                {{ row.qualified ? '合格' : '超差' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.head .page-title { margin: 0; }
.kv { display: flex; flex-wrap: wrap; gap: 10px 28px; font-size: 13px; }
.kv .k { color: #5c6b87; }
.actions { display: flex; gap: 10px; align-items: center; }
</style>
