<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const balances = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    balances.value = await http.get('/balances')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
})

const rows = computed(() => {
  const list = []
  for (const b of balances.value) {
    for (const r of b.ranges || []) {
      list.push({
        code: b.code,
        uniqueNo: b.uniqueNo,
        department: b.department,
        mode: b.internalCalibrationMode,
        maxCapacityG: r.maxCapacityG,
        divisionMg: r.divisionMg,
        weightSetNo: r.weightSetNo,
        weights: (r.weights || []).join(' / '),
        unit: b.defaultUnit,
        status: b.status
      })
    }
  }
  return list
})
</script>

<template>
  <div>
    <h1 class="page-title">天平清单</h1>
    <div class="card">
      <h2>天平与砝码配置<span class="hint">设备参数的唯一来源，由复核确认人员在后续批次维护</span></h2>
      <el-table :data="rows" v-loading="loading" size="small" empty-text="暂无天平">
        <el-table-column prop="code" label="天平编号" width="90" />
        <el-table-column prop="uniqueNo" label="天平唯一号" width="150" />
        <el-table-column prop="department" label="科室" width="100" />
        <el-table-column prop="mode" label="内校方式" width="90" align="center" />
        <el-table-column label="量程 / 分度值" min-width="170">
          <template #default="{ row }">{{ row.maxCapacityG }} g / {{ row.divisionMg }} mg</template>
        </el-table-column>
        <el-table-column prop="weightSetNo" label="砝码组" width="120" />
        <el-table-column label="标准砝码（g）" min-width="150">
          <template #default="{ row }">{{ row.weights }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="默认单位" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="note" style="margin-top: 10px">
        默认单位只用于识别不到单位时的兜底与一致性校验；示值实际以天平显示屏上的显示为准。
      </div>
    </div>
  </div>
</template>
