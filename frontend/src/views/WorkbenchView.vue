<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { auth } from '../stores/auth'
import { MAX_VIDEO_BYTES, MAX_VIDEO_LABEL } from '../config/upload'

const router = useRouter()
const balances = ref([])
const videos = ref([])
const balanceId = ref(null)
const recordedAt = ref(new Date())
const recordedAtTouched = ref(false)
const selectedFile = ref(null)
const uploading = ref(false)
const analyzingId = ref(null)
const progress = ref(0)
const loading = ref(false)
const mobileEntry = ref(null)
const mobileQrVersion = ref(Date.now())
const compact = ref(false)
let refreshTimer = null

const statusMap = {
  UPLOADED: { text: '已上传', type: 'info' },
  ANALYZING: { text: '识别中', type: 'warning' },
  ANALYZED: { text: '待确认', type: 'success' },
  FAILED: { text: '识别失败', type: 'danger' }
}

const calibrationStatusMap = {
  DRAFT: { text: '待确认', type: 'success' },
  SUBMITTED: { text: '待复核', type: 'warning' },
  REVIEWED: { text: '已复核', type: 'info' },
  VOIDED: { text: '已作废', type: 'danger' }
}

const balanceOptions = computed(() =>
  balances.value.map((b) => ({
    id: b.id,
    label: `${b.code}（${b.uniqueNo}）`
  }))
)

onMounted(async () => {
  syncCompact()
  window.addEventListener('resize', syncCompact)
  await Promise.all([loadBalances(), loadVideos(), loadMobileEntry()])
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') loadVideos(true)
  }, 3000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('resize', syncCompact)
})

function syncCompact() {
  compact.value = window.innerWidth <= 1200
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadVideos(true)
}

function displayStatus(row) {
  if (row.calibrationStatus === 'DRAFT' && row.calibrationReviewResult === 'REJECTED') {
    return { text: '已退回', type: 'danger' }
  }
  if (row.status === 'ANALYZED' && row.calibrationStatus) {
    return calibrationStatusMap[row.calibrationStatus] || { text: row.calibrationStatus, type: 'info' }
  }
  return statusMap[row.status] || { text: row.status, type: 'info' }
}

function isReturned(row) {
  return row.calibrationStatus === 'DRAFT' && row.calibrationReviewResult === 'REJECTED'
}

function openCalibration(row) {
  const suffix = row.calibrationStatus === 'DRAFT' ? '/edit' : ''
  router.push({
    path: `/calibrations/${row.calibrationId}${suffix}`,
    query: { returnTo: '/workbench' }
  })
}

async function loadMobileEntry() {
  try {
    mobileEntry.value = await http.get('/system/mobile-entry')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function copyMobileLink() {
  if (!mobileEntry.value?.url) return
  try {
    await navigator.clipboard.writeText(mobileEntry.value.url)
    ElMessage.success('手机上传地址已复制')
  } catch {
    ElMessage.info(mobileEntry.value.url)
  }
}

async function loadBalances() {
  try {
    balances.value = await http.get('/balances')
    if (balances.value.length && !balanceId.value) {
      balanceId.value = balances.value[0].id
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadVideos(silent = false) {
  if (!silent) loading.value = true
  try {
    videos.value = await http.get('/videos')
  } catch (err) {
    if (!silent) ElMessage.error(err.message)
  } finally {
    if (!silent) loading.value = false
  }
}

function onFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const ext = raw.name.split('.').pop().toLowerCase()
  if (!['mp4', 'mov', 'm4v', 'avi', 'mkv'].includes(ext)) {
    ElMessage.error('仅支持 mp4、mov、m4v、avi、mkv 格式')
    return
  }
  if (raw.size > MAX_VIDEO_BYTES) {
    ElMessage.error(`单个视频不能超过 ${MAX_VIDEO_LABEL}`)
    return
  }
  selectedFile.value = raw
  if (!recordedAtTouched.value) {
    recordedAt.value = new Date()
  }
}

function markRecordedAtTouched() {
  recordedAtTouched.value = true
}

async function startUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择校验视频')
    return
  }
  if (!balanceId.value) {
    ElMessage.warning('请选择天平')
    return
  }
  const form = new FormData()
  form.append('file', selectedFile.value)
  form.append('balanceId', balanceId.value)
  form.append('recordedAt', localDateTime(new Date(recordedAt.value)))

  uploading.value = true
  progress.value = 0
  try {
    const uploaded = await http.post('/videos', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (event.total) {
          progress.value = Math.round((event.loaded / event.total) * 100)
        }
      }
    })
    ElMessage.info('上传完成，开始识别九次称量')
    await http.post(`/videos/${uploaded.id}/analyze`)
    ElMessage.info('识别任务已提交，正在后台处理')
    const analyzed = await waitForAnalysis(uploaded.id)
    selectedFile.value = null
    recordedAt.value = new Date()
    recordedAtTouched.value = false
    progress.value = 0
    await loadVideos()
    if (analyzed.status === 'ANALYZED' && analyzed.calibrationId) {
      ElMessage.success('识别完成，已生成校验草稿')
      router.push({
        path: `/calibrations/${analyzed.calibrationId}/edit`,
        query: { returnTo: '/workbench' }
      })
    } else {
      ElMessage.error(analyzed.message || '识别失败，请检查视频后重试')
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    uploading.value = false
  }
}

async function analyzeVideo(row) {
  analyzingId.value = row.id
  try {
    await http.post(`/videos/${row.id}/analyze`)
    const analyzed = await waitForAnalysis(row.id)
    await loadVideos()
    if (analyzed.status !== 'ANALYZED' || !analyzed.calibrationId) {
      ElMessage.error(analyzed.message || '识别失败')
      return
    }
    ElMessage.success('识别完成，已生成校验草稿')
    router.push({
      path: `/calibrations/${analyzed.calibrationId}/edit`,
      query: { returnTo: '/workbench' }
    })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    analyzingId.value = null
  }
}

async function waitForAnalysis(videoId) {
  for (let attempt = 0; attempt < 600; attempt += 1) {
    const video = await http.get(`/videos/${videoId}`)
    if (video.status === 'ANALYZED' || video.status === 'FAILED') {
      return video
    }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error('视频识别超时，请稍后刷新列表查看状态')
}

async function removeVideo(row) {
  await ElMessageBox.confirm(`确定删除「${row.originalName}」吗？`, '删除视频', { type: 'warning' })
  try {
    await http.delete(`/videos/${row.id}`)
    ElMessage.success('已删除')
    await loadVideos()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function localDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
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
    <h1 class="page-title">检验工作台</h1>

    <div v-if="mobileEntry" class="card mobile-entry-card">
      <div class="mobile-copy">
        <div class="mobile-kicker">手机上传入口</div>
        <h2>扫码在手机上上传校验视频</h2>
        <p>手机与电脑连接同一局域网。手机只负责上传，识别和记录保存在电脑端。</p>
        <div class="mobile-url">{{ mobileEntry.url }}</div>
        <el-button @click="copyMobileLink">复制手机地址</el-button>
      </div>
      <div class="mobile-qr">
        <img :src="`/api/system/mobile-entry/qr?v=${mobileQrVersion}`" alt="手机上传入口二维码" />
        <span>微信或浏览器扫码</span>
      </div>
    </div>

    <div class="card">
      <h2><span class="no">1</span>导入校验视频<span class="hint">视频由检验人员按称量 SOP 拍摄完成后在手机端上传，系统从视频文件开始处理</span></h2>

      <div class="pick">
        <span class="label">本次校验的天平</span>
        <el-select v-model="balanceId" placeholder="请选择天平" style="width: 260px">
          <el-option v-for="item in balanceOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <span class="note">天平信息来自天平清单，识别阶段会据此带出量程、分度值与砝码组。</span>
        <span class="label">拍摄开始时间</span>
        <el-date-picker
          v-model="recordedAt"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          :clearable="false"
          placeholder="视频实际拍摄时间"
          @change="markRecordedAtTouched"
          style="width: 190px"
        />
        <span class="note">默认取当前时间，可手动调整</span>
      </div>

      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept="video/*"
        :on-change="onFileChange"
        class="uploader"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把校验视频拖到此处，或<em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 mp4、mov、m4v、avi、mkv，单个文件不超过 {{ MAX_VIDEO_LABEL }}</div>
        </template>
      </el-upload>

      <div v-if="selectedFile" class="selected">
        <div>
          <div class="file-name">{{ selectedFile.name }}</div>
          <div class="note">{{ formatSize(selectedFile.size) }}</div>
        </div>
        <el-button type="primary" :loading="uploading" @click="startUpload">上传并自动识别</el-button>
      </div>

      <el-progress v-if="uploading || progress > 0" :percentage="progress" :stroke-width="8" style="margin-top: 12px" />
    </div>

    <div class="card">
      <h2><span class="no">2</span>我的上传记录<span class="hint">复核确认人员与管理员可以看到全部记录</span></h2>
      <el-table :data="videos" v-loading="loading" size="small" empty-text="还没有上传记录">
        <el-table-column label="视频文件" min-width="180">
          <template #default="{ row }">
            <div class="file-cell" :title="row.originalName">{{ row.originalName }}</div>
            <div v-if="isReturned(row)" class="return-note" :title="row.calibrationReviewComment">
              退回：{{ row.calibrationReviewComment || '请查看记录详情' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="!compact" label="大小" width="75">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column prop="balanceCode" label="天平" width="65" />
        <el-table-column v-if="!compact" prop="uploaderName" label="上传人" width="80" />
        <el-table-column label="上传时间" width="130">
          <template #default="{ row }">
            <div class="datetime-cell">
              <span>{{ datePart(row.uploadedAt) }}</span>
              <small>{{ timePart(row.uploadedAt) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="displayStatus(row).type" size="small">
              {{ displayStatus(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="!compact"
          prop="message"
          label="说明"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UPLOADED' && row.uploaderName === auth.user?.displayName"
              link
              type="primary"
              :loading="analyzingId === row.id"
              @click="analyzeVideo(row)"
            >开始识别</el-button>
            <el-button
              v-else-if="row.status === 'FAILED' && row.uploaderName === auth.user?.displayName && row.sourceAvailable !== false"
              link
              type="primary"
              :loading="analyzingId === row.id"
              @click="analyzeVideo(row)"
            >重新识别</el-button>
            <span
              v-else-if="row.status === 'FAILED' && row.sourceAvailable === false"
              class="note"
            >源视频已清理，请重新上传</span>
            <el-button
              v-else-if="row.status === 'ANALYZED' && row.calibrationId"
              link
              type="primary"
              @click="openCalibration(row)"
            >{{ isReturned(row) ? '修改退回记录' : row.calibrationStatus === 'DRAFT' ? '确认结果' : '查看记录' }}</el-button>
            <el-button
              v-if="row.status === 'UPLOADED' && row.uploaderName === auth.user?.displayName"
              link
              type="danger"
              @click="removeVideo(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="note" style="margin-top: 10px">
        上传后自动识别九次称量并生成校验草稿；识别完成后请人工确认过程信息，再提交复核。
      </div>
    </div>
  </div>
</template>

<style scoped>
.pick { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.pick .label { font-size: 13px; color: #5c6b87; }
.uploader { display: block; }
.uploader :deep(.el-upload) { width: 100%; }
.uploader :deep(.el-upload-dragger) { padding: 22px 10px; background: #fafcff; border-color: #b9c6db; }
.selected {
  margin-top: 12px;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fbfcfe;
}
.file-name { font-size: 13px; font-weight: 700; margin-bottom: 4px; }
.mobile-entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: linear-gradient(120deg, #f5f9ff 0%, #eaf3ff 100%);
  border-color: #c7d9f1;
}
.mobile-kicker { color: #1f5c9d; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.mobile-copy h2 { margin: 5px 0 6px; font-size: 17px; }
.mobile-copy p { margin: 0 0 10px; color: #60718c; font-size: 12.5px; }
.mobile-url { font-family: Consolas, monospace; color: #245b94; font-size: 12px; margin-bottom: 10px; word-break: break-all; }
.mobile-qr { flex: 0 0 132px; text-align: center; }
.mobile-qr img { display: block; width: 132px; height: 132px; border: 6px solid #fff; border-radius: 10px; background: #fff; }
.mobile-qr span { display: block; margin-top: 5px; color: #7c8ba1; font-size: 10.5px; }
.file-cell { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.return-note {
  margin-top: 4px;
  color: #b03a2e;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.datetime-cell { line-height: 1.35; white-space: nowrap; }
.datetime-cell span, .datetime-cell small { display: block; }
.datetime-cell small { color: #7a889f; font-size: 11px; }
@media (max-width: 820px) {
  .mobile-entry-card { align-items: flex-start; flex-direction: column; }
  .selected { align-items: flex-start; flex-direction: column; gap: 10px; }
}
</style>
