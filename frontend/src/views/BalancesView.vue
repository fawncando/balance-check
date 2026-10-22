<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'

const balances = ref([])
const weightSets = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({
  id: null,
  code: '',
  uniqueNo: '',
  department: '',
  internalCalibrationMode: '手动',
  defaultUnit: 'g',
  status: 'ACTIVE',
  ranges: []
})

const canMaintain = computed(() => ['ADMIN', 'REVIEWER'].includes(auth.user?.role))
const dialogTitle = computed(() => (form.id ? '编辑天平' : '新增天平'))

onMounted(async () => {
  await Promise.all([load(), loadWeightSets()])
})

async function load() {
  loading.value = true
  try {
    balances.value = await http.get('/balances')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function loadWeightSets() {
  try {
    weightSets.value = await http.get('/weight-sets')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function weightSetLabel(set) {
  return `${set.setNo}（${(set.weights || []).join(' / ')} g）`
}

function newRange(seqNo) {
  return { seqNo, maxCapacityG: null, divisionMg: null, weightSetId: weightSets.value[0]?.id ?? null }
}

function openCreate() {
  form.id = null
  form.code = ''
  form.uniqueNo = ''
  form.department = ''
  form.internalCalibrationMode = '手动'
  form.defaultUnit = 'g'
  form.status = 'ACTIVE'
  form.ranges = [newRange(1)]
  dialogVisible.value = true
}

function openEdit(balance) {
  form.id = balance.id
  form.code = balance.code
  form.uniqueNo = balance.uniqueNo
  form.department = balance.department
  form.internalCalibrationMode = balance.internalCalibrationMode || '手动'
  form.defaultUnit = balance.defaultUnit || 'g'
  form.status = balance.status || 'ACTIVE'
  form.ranges = (balance.ranges || []).map((range, index) => ({
    seqNo: index + 1,
    maxCapacityG: Number(range.maxCapacityG),
    divisionMg: Number(range.divisionMg),
    weightSetId: range.weightSetId ?? weightSets.value[0]?.id ?? null
  }))
  if (form.ranges.length === 0) form.ranges = [newRange(1)]
  dialogVisible.value = true
}

function addRange() {
  if (form.ranges.length >= 2) {
    ElMessage.warning('单台天平最多配置两个量程')
    return
  }
  form.ranges.push(newRange(form.ranges.length + 1))
}

function removeRange(index) {
  if (form.ranges.length === 1) {
    ElMessage.warning('至少保留一个量程')
    return
  }
  form.ranges.splice(index, 1)
  form.ranges.forEach((range, idx) => { range.seqNo = idx + 1 })
}

function payload() {
  return {
    code: form.code.trim(),
    uniqueNo: form.uniqueNo.trim(),
    department: form.department.trim(),
    rangeCount: form.ranges.length,
    internalCalibrationMode: form.internalCalibrationMode,
    defaultUnit: form.defaultUnit,
    status: form.status,
    ranges: form.ranges.map((range, index) => ({
      seqNo: index + 1,
      maxCapacityG: Number(range.maxCapacityG),
      divisionMg: Number(range.divisionMg),
      weightSetId: range.weightSetId
    }))
  }
}

async function save() {
  if (!form.code.trim() || !form.uniqueNo.trim() || !form.department.trim()) {
    ElMessage.warning('请填写天平编号、设备唯一号与科室')
    return
  }
  if (form.ranges.some((range) => !range.maxCapacityG || !range.divisionMg || !range.weightSetId)) {
    ElMessage.warning('请补齐量程上限、分度值与砝码组')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await http.put(`/balances/${form.id}`, payload())
      ElMessage.success('天平已更新')
    } else {
      await http.post('/balances', payload())
      ElMessage.success('天平已新增')
    }
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(balance) {
  const next = balance.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const label = next === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`${label}天平 ${balance.code} 后，${next === 'ACTIVE' ? '可以继续用于新建校验记录' : '将不再出现在新建记录的备选清单中，历史记录不受影响'}。`, `${label}天平`, { type: 'warning' })
  } catch {
    return
  }
  const request = {
    code: balance.code,
    uniqueNo: balance.uniqueNo,
    department: balance.department,
    rangeCount: (balance.ranges || []).length,
    internalCalibrationMode: balance.internalCalibrationMode,
    defaultUnit: balance.defaultUnit,
    status: next,
    ranges: (balance.ranges || []).map((range, index) => ({
      seqNo: index + 1,
      maxCapacityG: Number(range.maxCapacityG),
      divisionMg: Number(range.divisionMg),
      weightSetId: range.weightSetId
    }))
  }
  try {
    await http.put(`/balances/${balance.id}`, request)
    ElMessage.success(`已${label}`)
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}
</script>

<template>
  <div>
    <div class="head">
      <h1 class="page-title">天平清单</h1>
      <el-button v-if="canMaintain" type="primary" @click="openCreate">新增天平</el-button>
    </div>

    <div class="card">
      <h2>
        天平与砝码配置
        <span class="hint">{{ canMaintain ? '设备参数的唯一来源，由复核确认人员维护' : '只读：维护功能仅对复核确认人员与管理员开放' }}</span>
      </h2>
      <el-table :data="balances" v-loading="loading" size="small" empty-text="暂无天平">
        <el-table-column prop="code" label="天平编号" width="90" />
        <el-table-column prop="uniqueNo" label="天平唯一号" width="140" />
        <el-table-column prop="department" label="科室" width="90" />
        <el-table-column prop="internalCalibrationMode" label="内校方式" width="86" align="center" />
        <el-table-column label="量程 / 分度值 / 砝码组" min-width="260">
          <template #default="{ row }">
            <div v-for="range in row.ranges" :key="range.seqNo" class="range-line">
              R{{ range.seqNo }}：{{ range.maxCapacityG }} g / {{ range.divisionMg }} mg · {{ range.weightSetNo }}
              <span class="note">（{{ (range.weights || []).join(' / ') }} g）</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="defaultUnit" label="默认单位" width="86" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canMaintain" label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="note" style="margin-top: 10px">
        默认单位只用于识别不到单位时的兜底与一致性校验；示值实际以天平显示屏上的显示为准。停用的天平不会出现在新建记录的备选清单中，历史记录不受影响。
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
      <el-form label-width="96px">
        <div class="grid">
          <el-form-item label="天平编号">
            <el-input v-model="form.code" placeholder="如 F003" maxlength="20" />
          </el-form-item>
          <el-form-item label="设备唯一号">
            <el-input v-model="form.uniqueNo" placeholder="设备铭牌唯一号" maxlength="40" />
          </el-form-item>
          <el-form-item label="科室">
            <el-input v-model="form.department" maxlength="50" />
          </el-form-item>
          <el-form-item label="内部校正">
            <el-select v-model="form.internalCalibrationMode" style="width: 100%">
              <el-option label="自动" value="自动" />
              <el-option label="手动" value="手动" />
              <el-option label="NA" value="NA" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认单位">
            <el-select v-model="form.defaultUnit" style="width: 100%">
              <el-option label="g" value="g" />
              <el-option label="mg" value="mg" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>

        <div class="ranges-head">
          <span>量程配置</span>
          <el-button link type="primary" :disabled="form.ranges.length >= 2" @click="addRange">增加量程</el-button>
        </div>
        <el-table :data="form.ranges" size="small" class="ranges">
          <el-table-column label="序号" width="60" align="center">
            <template #default="{ row }">R{{ row.seqNo }}</template>
          </el-table-column>
          <el-table-column label="量程上限（g）" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.maxCapacityG" :min="0.001" :controls="false" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="分度值（mg）" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.divisionMg" :min="0.001" :controls="false" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="砝码组" min-width="220">
            <template #default="{ row }">
              <el-select v-model="row.weightSetId" size="small" style="width: 100%">
                <el-option v-for="set in weightSets" :key="set.id" :label="weightSetLabel(set)" :value="set.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" :disabled="form.ranges.length === 1" @click="removeRange($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="note" style="margin-top: 8px">
          保存后量程整体替换；历史校验记录保存的是当时的限度值，不随天平配置变化。
        </div>
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
.range-line { font-size: 12.5px; line-height: 1.8; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.ranges-head { display: flex; align-items: center; justify-content: space-between; margin: 6px 0 8px; font-size: 13px; color: #43536f; }
.ranges { margin-bottom: 4px; }
</style>
