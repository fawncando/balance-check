<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { limits, qualified, scaleForDivision, toGram } from '../utils/judgement'

const route = useRoute()
const router = useRouter()

const balances = ref([])
const balanceId = ref(null)
const calibrationTime = ref(new Date())
const form = reactive({
  cleanOk: true,
  levelOk: true,
  internalCalibrationResult: '是',
  remark: '无'
})
const rows = ref([])
const recordId = ref(null)
const recordNo = ref('')
const loading = ref(false)
const saving = ref(false)

const currentBalance = computed(() => balances.value.find((b) => b.id === balanceId.value))
const filled = computed(() => rows.value.filter((r) => r.displayValue !== null && r.displayValue !== '').length)
const overall = computed(() => rows.value.length > 0 && rows.value.every((r) => resultOf(r).ok === true))

onMounted(async () => {
  loading.value = true
  try {
    balances.value = await http.get('/balances')
    const id = route.params.id
    if (id) {
      const record = await http.get(`/calibrations/${id}`)
      applyRecord(record)
    } else {
      balanceId.value = route.query.balanceId ? Number(route.query.balanceId) : balances.value[0]?.id || null
      if (currentBalance.value) buildRows(currentBalance.value)
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
})

function buildRows(balance) {
  rows.value = []
  for (const range of balance.ranges || []) {
    const scale = scaleForDivision(range.divisionMg)
    range.weights.forEach((weight, index) => {
      for (let measure = 1; measure <= 3; measure += 1) {
        rows.value.push({
          rangeSeq: range.seqNo,
          weightSeq: index + 1,
          measureSeq: measure,
          nominalG: Number(weight),
          divisionMg: Number(range.divisionMg),
          scale,
          displayValue: null,
          displayUnit: 'g',
          time: ''
        })
      }
    })
  }
}

function applyRecord(record) {
  recordId.value = record.id
  recordNo.value = record.recordNo
  balanceId.value = record.balanceId
  calibrationTime.value = new Date(record.calibrationTime)
  form.cleanOk = record.cleanOk ?? true
  form.levelOk = record.levelOk ?? true
  form.internalCalibrationResult = record.internalCalibrationResult || '是'
  form.remark = record.remark || '无'
  rows.value = (record.measurements || []).map((m) => {
    const balance = balances.value.find((b) => b.id === record.balanceId)
    const range = balance?.ranges?.find((r) => r.seqNo === m.rangeSeq)
    return {
      rangeSeq: m.rangeSeq,
      weightSeq: m.weightSeq,
      measureSeq: m.measureSeq,
      nominalG: Number(m.nominalG),
      divisionMg: Number(range?.divisionMg || 0.1),
      scale: scaleForDivision(range?.divisionMg || 0.1),
      displayValue: Number(m.displayValue),
      displayUnit: m.displayUnit || 'g',
      time: m.measuredAt ? String(m.measuredAt).slice(11, 16) : ''
    }
  })
}

function onBalanceChange() {
  if (currentBalance.value) buildRows(currentBalance.value)
}

function resultOf(row) {
  const { lower, upper } = limits(row.nominalG, row.scale)
  if (row.displayValue === null || row.displayValue === '' || Number.isNaN(Number(row.displayValue))) {
    return { lower, upper, valueG: null, ok: null }
  }
  const valueG = toGram(row.displayValue, row.displayUnit)
  return { lower, upper, valueG, ok: qualified(valueG, row.nominalG, row.scale) }
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function localDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function measuredAt(row) {
  if (!row.time) return null
  const date = new Date(calibrationTime.value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${row.time}:00`
}

function payload() {
  return {
    balanceId: balanceId.value,
    calibrationTime: localDateTime(new Date(calibrationTime.value)),
    cleanOk: form.cleanOk,
    levelOk: form.levelOk,
    internalCalibrationResult: form.internalCalibrationResult,
    remark: form.remark || '无',
    measurements: rows.value.map((row) => ({
      rangeSeq: row.rangeSeq,
      weightSeq: row.weightSeq,
      measureSeq: row.measureSeq,
      nominalG: row.nominalG,
      displayValue: Number(row.displayValue),
      displayUnit: row.displayUnit,
      measuredAt: measuredAt(row),
      manuallyCorrected: true
    }))
  }
}

async function save(submitAfter = false) {
  if (!balanceId.value) {
    ElMessage.warning('请选择天平')
    return
  }
  if (rows.value.length === 0 || filled.value < rows.value.length) {
    ElMessage.warning(`请填写全部 ${rows.value.length} 次示值`)
    return
  }
  saving.value = true
  try {
    const record = recordId.value
      ? await http.put(`/calibrations/${recordId.value}`, payload())
      : await http.post('/calibrations', payload())
    recordId.value = record.id
    if (submitAfter) {
      await http.post(`/calibrations/${record.id}/submit`)
      ElMessage.success('已提交复核')
    } else {
      ElMessage.success('已保存草稿')
    }
    router.replace(`/calibrations/${record.id}`)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading">
    <div class="head">
      <h1 class="page-title">{{ recordId ? '编辑校验记录' : '新建校验记录' }}</h1>
      <span v-if="recordNo" class="note">记录编号 {{ recordNo }}</span>
    </div>

    <div class="card">
      <h2><span class="no">1</span>校验信息</h2>
      <div class="grid">
        <div class="field">
          <label>天平</label>
          <el-select v-model="balanceId" :disabled="!!recordId" style="width: 260px" @change="onBalanceChange">
            <el-option
              v-for="b in balances"
              :key="b.id"
              :label="`${b.code}（${b.uniqueNo}）`"
              :value="b.id"
            />
          </el-select>
        </div>
        <div class="field">
          <label>校验时间</label>
          <el-date-picker v-model="calibrationTime" type="datetime" format="YYYY-MM-DD HH:mm" />
        </div>
        <div class="field full">
          <label>过程信息</label>
          <div class="radios">
            <span>清洁</span>
            <el-radio-group v-model="form.cleanOk">
              <el-radio :value="true">是</el-radio>
              <el-radio :value="false">否</el-radio>
            </el-radio-group>
            <span>水平调节</span>
            <el-radio-group v-model="form.levelOk">
              <el-radio :value="true">是</el-radio>
              <el-radio :value="false">否</el-radio>
            </el-radio-group>
            <span>内部校正</span>
            <el-radio-group v-model="form.internalCalibrationResult">
              <el-radio value="是">通过</el-radio>
              <el-radio value="否">不通过</el-radio>
              <el-radio value="NA">NA</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <h2>
        <span class="no">2</span>九次称量
        <span class="hint">示值按天平显示填写，单位以天平显示为准；限度与判定统一用 g</span>
      </h2>
      <el-table :data="rows" size="small">
        <el-table-column label="量程" width="80" align="center">
          <template #default="{ row }">R{{ row.rangeSeq }}</template>
        </el-table-column>
        <el-table-column label="砝码" width="90" align="right">
          <template #default="{ row }">{{ row.nominalG }} g</template>
        </el-table-column>
        <el-table-column label="次序" width="70" align="center">
          <template #default="{ row }">{{ row.measureSeq }}</template>
        </el-table-column>
        <el-table-column label="测量示值" width="190">
          <template #default="{ row }">
            <el-input v-model="row.displayValue" size="small" placeholder="输入示值" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90" align="center">
          <template #default="{ row }">
            <el-select v-model="row.displayUnit" size="small">
              <el-option label="g" value="g" />
              <el-option label="mg" value="mg" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="测量时间" width="120" align="center">
          <template #default="{ row }">
            <el-time-picker v-model="row.time" format="HH:mm" value-format="HH:mm" size="small" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="限度（g）" min-width="190">
          <template #default="{ row }">
            {{ resultOf(row).lower }} ~ {{ resultOf(row).upper }}
          </template>
        </el-table-column>
        <el-table-column label="判定" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="resultOf(row).ok === true" type="success" size="small">合格</el-tag>
            <el-tag v-else-if="resultOf(row).ok === false" type="danger" size="small">超差</el-tag>
            <span v-else class="note">待填</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="note" style="margin-top: 8px">
        已填 {{ filled }} / {{ rows.length }} 次；整机结论：{{ overall ? '外部校正通过' : '未通过或待填' }}
      </div>
    </div>

    <div class="card">
      <h2><span class="no">3</span>结论与提交</h2>
      <div class="field">
        <label>备注</label>
        <el-input v-model="form.remark" type="textarea" :rows="2" style="max-width: 640px" />
      </div>
      <div class="actions">
        <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="save(true)">保存并提交复核</el-button>
        <el-button link @click="router.push('/records')">返回记录列表</el-button>
      </div>
      <div class="note">提交后记录进入待复核，不能再编辑；复核确认后永久锁定。</div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: baseline; gap: 14px; }
.grid { display: flex; flex-wrap: wrap; gap: 14px 26px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field.full { width: 100%; }
.field label { font-size: 12.5px; color: #5c6b87; }
.radios { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
.radios span { color: #5c6b87; margin-left: 8px; }
.radios span:first-child { margin-left: 0; }
.actions { display: flex; align-items: center; gap: 10px; margin: 14px 0 8px; }
</style>
