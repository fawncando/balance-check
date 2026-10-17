<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { auth } from '../stores/auth'
import { LIMIT_PERCENT, chartScale, deviationSeries, reviewResultText, reviewStateOf } from '../utils/review'

const route = useRoute()
const router = useRouter()
const record = ref(null)
const loading = ref(false)

const CHART = { width: 660, height: 200, padX: 46, padY: 26 }

const state = computed(() => reviewStateOf(record.value?.status, record.value?.reviewResult))
const isReviewer = computed(() => ['REVIEWER', 'ADMIN'].includes(auth.user?.role))
const isOwner = computed(() => record.value?.inspectorId != null && record.value.inspectorId === auth.user?.id)
const canEdit = computed(() => record.value?.status === 'DRAFT' && (isOwner.value || auth.user?.role === 'ADMIN'))
const canReview = computed(() => isReviewer.value && record.value?.status === 'SUBMITTED')
const canReopen = computed(() => isReviewer.value && record.value?.status === 'REVIEWED')
const canExport = computed(() => isReviewer.value && !!record.value)

const series = computed(() => deviationSeries(record.value?.measurements))
const plotted = computed(() => series.value.filter((item) => item.pct !== null))
const scale = computed(() => chartScale(series.value))
const bandTop = computed(() => yOf(LIMIT_PERCENT))
const bandBottom = computed(() => yOf(-LIMIT_PERCENT))
const zeroY = computed(() => yOf(0))
const polyline = computed(() => plotted.value
  .map((item) => `${xOf(item.index).toFixed(1)},${yOf(item.pct).toFixed(1)}`)
  .join(' '))

onMounted(load)

function xOf(index) {
  const count = Math.max(series.value.length, 1)
  const usable = CHART.width - CHART.padX * 2
  if (count === 1) return CHART.padX + usable / 2
  return CHART.padX + (usable * index) / (count - 1)
}

function yOf(pct) {
  const half = CHART.height / 2 - CHART.padY
  return CHART.height / 2 - (pct / scale.value) * half
}

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

function isCancel(error) {
  return error === 'cancel' || error === 'close'
}

async function submit() {
  try {
    await ElMessageBox.confirm('提交后记录不能再编辑，确定提交复核吗？', '提交复核', { type: 'warning' })
  } catch {
    return
  }
  try {
    record.value = await http.post(`/calibrations/${record.value.id}/submit`)
    ElMessage.success('已提交复核')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function review(result, comment) {
  try {
    record.value = await http.post(`/calibrations/${record.value.id}/review`, { result, comment })
    ElMessage.success(result === 'APPROVED' ? '已复核通过，记录已锁定' : '已退回检验人员修改')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function approve() {
  try {
    const { value } = await ElMessageBox.prompt('复核意见（可不填）', `通过 ${record.value.recordNo}`, {
      inputPlaceholder: '例如：示值稳定，与砝码组配置一致',
      inputType: 'textarea',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
    await review('APPROVED', value)
  } catch (err) {
    if (!isCancel(err)) ElMessage.error(err.message || '操作未完成')
  }
}

async function reject() {
  try {
    const { value } = await ElMessageBox.prompt('退回必须说明原因，检验人员会据此修改', `退回 ${record.value.recordNo}`, {
      inputPlaceholder: '例如：第 5 次称量示值超差，请确认砝码放置',
      inputType: 'textarea',
      inputValidator: (text) => (text && text.trim() ? true : '退回必须填写复核意见'),
      confirmButtonText: '确认退回',
      cancelButtonText: '取消'
    })
    await review('REJECTED', value)
  } catch (err) {
    if (!isCancel(err)) ElMessage.error(err.message || '操作未完成')
  }
}

async function reopen() {
  try {
    await ElMessageBox.confirm('解锁后记录回到草稿，检验人员可以修改并重新提交。确定解锁吗？', '解锁记录', { type: 'warning' })
  } catch {
    return
  }
  try {
    record.value = await http.post(`/calibrations/${record.value.id}/reopen`)
    ElMessage.success('已解锁，记录回到草稿')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function exportSheet() {
  window.open(`/api/calibrations/export?id=${record.value.id}`, '_blank', 'noopener')
}
</script>

<template>
  <div v-loading="loading">
    <div class="head">
      <h1 class="page-title">校验记录详情</h1>
      <div class="actions">
        <el-button v-if="canEdit" @click="router.push(`/calibrations/${record.id}/edit`)">编辑</el-button>
        <el-button v-if="canEdit" type="primary" @click="submit">提交复核</el-button>
        <el-button v-if="canReview" type="success" @click="approve">复核通过</el-button>
        <el-button v-if="canReview" type="danger" plain @click="reject">退回</el-button>
        <el-button v-if="canReopen" type="warning" plain @click="reopen">解锁</el-button>
        <el-button v-if="canExport" @click="exportSheet">记录表</el-button>
        <el-button link @click="router.push('/records')">返回列表</el-button>
      </div>
    </div>

    <div v-if="record">
      <div class="verdict" :class="record.externalResult === '是' ? 'ok' : 'bad'">
        <div>
          <div class="v-main">{{ record.externalResult === '是' ? '外部校正通过' : '外部校正不通过' }}</div>
          <div class="v-sub">
            {{ record.balanceCode }} · {{ record.calibrationTime }} · 检验人员 {{ record.inspectorName }}
            <span v-if="record.reviewerName"> · 复核人 {{ record.reviewerName }}</span>
          </div>
        </div>
        <el-tag :type="state.type" :effect="record.status === 'SUBMITTED' ? 'dark' : 'light'" size="large">
          {{ state.text }}
        </el-tag>
      </div>

      <div class="card">
        <h2>
          {{ record.recordNo }}
          <el-tag :type="state.type" size="small" style="margin-left: 8px">{{ state.text }}</el-tag>
        </h2>
        <div class="kv">
          <div><span class="k">天平：</span>{{ record.balanceCode }}</div>
          <div><span class="k">设备唯一号：</span>{{ record.balanceUniqueNo }}</div>
          <div><span class="k">科室：</span>{{ record.department }}</div>
          <div><span class="k">校验时间：</span>{{ record.calibrationTime }}</div>
          <div><span class="k">检验人员：</span>{{ record.inspectorName }}</div>
          <div><span class="k">提交时间：</span>{{ record.submittedAt || '-' }}</div>
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
        <h2>复核信息</h2>
        <div class="kv">
          <div><span class="k">复核结论：</span>{{ reviewResultText(record.reviewResult) }}</div>
          <div><span class="k">复核人员：</span>{{ record.reviewerName || '-' }}</div>
          <div><span class="k">复核时间：</span>{{ record.reviewedAt || '-' }}</div>
          <div class="full"><span class="k">复核意见：</span>{{ record.reviewComment || '-' }}</div>
        </div>
        <div class="advice" :class="state.type" v-if="record.status === 'DRAFT' && record.reviewResult === 'REJECTED'">
          该记录已被退回，修改后可重新提交复核。
        </div>
        <div class="advice info" v-if="record.status === 'DRAFT' && record.reviewResult === 'APPROVED'">
          该记录已被复核人员解锁，修改后需重新提交复核。
        </div>
        <div class="advice" :class="state.type" v-if="record.status === 'REVIEWED'">
          记录已锁定为“已复核”，如需修改须由复核人员解锁。
        </div>
      </div>

      <div class="card">
        <h2>
          九次称量明细
          <span class="hint">示值以天平显示为准，判定统一用换算后的 g 值</span>
        </h2>
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

      <div class="card">
        <h2>
          示值偏差曲线
          <span class="hint">纵轴为相对标称值的偏差（%），绿色区间为 ±0.05% 允许带</span>
        </h2>
        <svg v-if="plotted.length" class="chart" :viewBox="`0 0 ${CHART.width} ${CHART.height}`" role="img">
          <rect
            class="band"
            :x="CHART.padX"
            :y="bandTop"
            :width="CHART.width - CHART.padX * 2"
            :height="Math.max(bandBottom - bandTop, 1)"
          />
          <line :x1="CHART.padX" :y1="zeroY" :x2="CHART.width - CHART.padX" :y2="zeroY" class="axis" />
          <text :x="CHART.padX - 8" :y="bandTop + 4" class="tick" text-anchor="end">+0.05%</text>
          <text :x="CHART.padX - 8" :y="zeroY + 4" class="tick" text-anchor="end">0</text>
          <text :x="CHART.padX - 8" :y="bandBottom + 4" class="tick" text-anchor="end">-0.05%</text>
          <polyline :points="polyline" class="line" />
          <g v-for="item in plotted" :key="item.label">
            <circle :cx="xOf(item.index)" :cy="yOf(item.pct)" r="4" :class="item.qualified ? 'dot-ok' : 'dot-bad'" />
            <text :x="xOf(item.index)" :y="CHART.height - 6" class="tick" text-anchor="middle">{{ item.label }}</text>
          </g>
        </svg>
        <div v-else class="note">暂无可绘制的称量数据</div>
        <div class="note" style="margin-top: 8px">
          偏差 =（示值 − 标称值）÷ 标称值 × 100%，红点为超差；横轴标注为“量程-砝码-次序”。
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.head .page-title { margin: 0; }
.verdict {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #dfe5ee;
  border-left: 4px solid #1e8e5a;
  border-radius: 6px;
  background: #f2faf5;
  padding: 12px 18px;
  margin-bottom: 14px;
}
.verdict.bad { border-left-color: #c0392b; background: #fdf4f3; }
.v-main { font-size: 17px; font-weight: 700; color: #1e8e5a; }
.verdict.bad .v-main { color: #c0392b; }
.v-sub { font-size: 12px; color: #5c6b87; margin-top: 4px; }
.kv { display: flex; flex-wrap: wrap; gap: 10px 28px; font-size: 13px; }
.kv .k { color: #5c6b87; }
.kv .full { width: 100%; }
.actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.advice { margin-top: 12px; padding: 8px 12px; border-radius: 4px; font-size: 12.5px; background: #fdf4f3; color: #b03a2e; }
.advice.info { background: #eef3fb; color: #1f4e8c; }
.advice.success { background: #f2faf5; color: #1e8e5a; }
.chart { width: 100%; height: 200px; }
.band { fill: #eaf6ef; }
.axis { stroke: #c9d4e4; stroke-dasharray: 4 4; }
.line { fill: none; stroke: #1f4e8c; stroke-width: 1.6; }
.dot-ok { fill: #1e8e5a; }
.dot-bad { fill: #c0392b; }
.tick { font-size: 10px; fill: #6d7a90; }
</style>
