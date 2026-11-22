<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CameraFilled, SwitchButton } from '@element-plus/icons-vue'
import http from '../api/http'
import { auth, logout } from '../stores/auth'

const balances = ref([])
const balanceId = ref(null)
const recordedAt = ref(new Date())
const selectedFile = ref(null)
const uploading = ref(false)
const progress = ref(0)
const stage = ref('idle')
const statusText = ref('等待选择视频')
const result = ref(null)
const recent = ref([])
let refreshTimer = null

const currentBalance = computed(() => balances.value.find((item) => item.id === balanceId.value))
const canUpload = computed(() => !!selectedFile.value && !!balanceId.value && !uploading.value)

onMounted(async () => {
  try {
    balances.value = await http.get('/balances')
    balanceId.value = balances.value[0]?.id || null
    await loadRecent()
    refreshTimer = window.setInterval(loadRecent, 5000)
  } catch (err) {
    ElMessage.error(err.message)
  }
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

function chooseFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const ext = file.name.split('.').pop().toLowerCase()
  if (!['mp4', 'mov', 'm4v', 'avi', 'mkv'].includes(ext)) {
    ElMessage.error('仅支持 mp4、mov、m4v、avi、mkv')
    return
  }
  if (file.size > 500 * 1024 * 1024) {
    ElMessage.error('视频不能超过 500MB')
    return
  }
  selectedFile.value = file
  if (Number(file.lastModified) > 0) recordedAt.value = new Date(file.lastModified)
  result.value = null
  stage.value = 'idle'
  statusText.value = '视频已选择，可以上传到电脑'
}

async function startUpload() {
  if (!canUpload.value) {
    ElMessage.warning('请选择天平和视频')
    return
  }
  uploading.value = true
  result.value = null
  stage.value = 'uploading'
  progress.value = 0
  statusText.value = '正在上传到电脑...'
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    form.append('balanceId', balanceId.value)
    form.append('recordedAt', localDateTime(new Date(recordedAt.value)))
    const uploaded = await http.post('/videos', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (event.total) {
          progress.value = Math.round((event.loaded / event.total) * 100)
        }
      }
    })
    stage.value = 'analyzing'
    statusText.value = '已上传，电脑端正在识别九次称量...'
    await http.post(`/videos/${uploaded.id}/analyze`)
    const analyzed = await waitForAnalysis(uploaded.id)
    result.value = analyzed
    selectedFile.value = null
    if (analyzed.status === 'ANALYZED') {
      stage.value = 'done'
      statusText.value = '识别完成，已生成电脑端校验草稿'
    } else {
      stage.value = 'failed'
      statusText.value = analyzed.message || '识别失败'
    }
    await loadRecent()
  } catch (err) {
    stage.value = 'failed'
    statusText.value = err.message
    ElMessage.error(err.message)
  } finally {
    uploading.value = false
  }
}

async function waitForAnalysis(videoId) {
  for (let attempt = 0; attempt < 600; attempt += 1) {
    const video = await http.get(`/videos/${videoId}`)
    if (video.status === 'ANALYZED' || video.status === 'FAILED') return video
    if (attempt % 5 === 0) {
      statusText.value = `电脑端正在识别... ${Math.floor(attempt / 5) + 1} 秒`
    }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error('识别超时，请在电脑端查看视频状态')
}

async function loadRecent() {
  try {
    recent.value = (await http.get('/videos')).slice(0, 5)
  } catch {
    recent.value = []
  }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function localDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function statusLabel(status) {
  return {
    UPLOADED: '已上传',
    ANALYZING: '识别中',
    ANALYZED: '待确认',
    FAILED: '失败'
  }[status] || status
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  } catch {
    return
  }
  await logout()
  window.location.href = '/login?redirect=/mobile'
}
</script>

<template>
  <div class="mobile-page">
    <header class="mobile-head">
      <div>
        <div class="eyebrow">天平校验 · 手机上传</div>
        <h1>上传到电脑</h1>
      </div>
      <button class="user-btn" @click="onLogout">
        {{ auth.user?.displayName }}
        <el-icon><SwitchButton /></el-icon>
      </button>
    </header>

    <main>
      <section class="hero">
        <div class="hero-icon"><el-icon><CameraFilled /></el-icon></div>
        <div>
          <strong>手机只负责拍摄和上传</strong>
          <p>视频直接传到本系统电脑端，九次称量识别由电脑后台完成。</p>
        </div>
      </section>

      <section class="card">
        <label class="field">
          <span>本次校验天平</span>
          <el-select v-model="balanceId" size="large" style="width: 100%">
            <el-option
              v-for="item in balances"
              :key="item.id"
              :label="`${item.code}（${item.uniqueNo}）`"
              :value="item.id"
            />
          </el-select>
          <small v-if="currentBalance">
            {{ currentBalance.rangeCount }} 个量程 · {{ currentBalance.department }}
          </small>
          <span>拍摄开始时间</span>
          <el-date-picker
            v-model="recordedAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            :clearable="false"
            placeholder="视频实际拍摄时间"
            style="width: 100%"
          />
        </label>

        <label class="file-picker">
          <input type="file" accept="video/*" @change="chooseFile" />
          <el-icon><CameraFilled /></el-icon>
          <strong>{{ selectedFile ? selectedFile.name : '选择或拍摄校验视频' }}</strong>
          <span>{{ selectedFile ? formatSize(selectedFile.size) : '支持 mp4、mov、m4v、avi、mkv' }}</span>
        </label>

        <div class="status" :class="stage">
          <span class="dot"></span>
          <span>{{ statusText }}</span>
        </div>

        <div v-if="stage === 'uploading'" class="progress-wrap">
          <el-progress :percentage="progress" :stroke-width="10" />
        </div>

        <el-button
          type="primary"
          size="large"
          class="upload-btn"
          :loading="uploading"
          :disabled="!canUpload"
          @click="startUpload"
        >
          {{ uploading ? '处理中...' : '上传到电脑并识别' }}
        </el-button>
      </section>

      <section v-if="result" class="card result" :class="stage">
        <div class="result-title">
          {{ stage === 'done' ? '已上传并识别完成' : '识别失败' }}
        </div>
        <p v-if="stage === 'done'">
          记录 {{ result.message?.match(/JC\d+/)?.[0] || '已生成' }} 已在电脑端创建。
          请在电脑端确认过程信息后提交复核。
        </p>
        <p v-else>{{ result.message || '请检查视频后重试' }}</p>
        <div v-if="stage === 'done'" class="quality">
          <span>置信度 {{ result.analysisConfidence || 0 }}%</span>
          <span>{{ result.identityVerified ? '人员卡已核验' : '人员身份待人工核验' }}</span>
        </div>
      </section>

      <section class="card recent">
        <div class="section-head">
          <strong>最近上传</strong>
          <span>{{ auth.user?.displayName }}</span>
        </div>
        <div v-if="recent.length === 0" class="empty">还没有上传记录</div>
        <div v-for="item in recent" :key="item.id" class="recent-row">
          <div>
            <strong>{{ item.originalName }}</strong>
            <small>{{ item.balanceCode }} · {{ formatSize(item.sizeBytes) }}</small>
          </div>
          <span :class="['state', item.status?.toLowerCase()]">{{ statusLabel(item.status) }}</span>
        </div>
      </section>
    </main>
    <footer>手机与电脑须连接同一局域网，上传完成后可关闭本页。</footer>
  </div>
</template>

<style scoped>
.mobile-page {
  min-height: 100%;
  background: #eef3fb;
  color: #1f2a44;
  padding-bottom: env(safe-area-inset-bottom);
}
.mobile-head {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: calc(14px + env(safe-area-inset-top)) 18px 14px;
  color: #fff;
  background: linear-gradient(135deg, #153b69, #1f5d9e);
}
.eyebrow { font-size: 11px; opacity: .72; letter-spacing: 1px; }
h1 { margin: 3px 0 0; font-size: 22px; }
.user-btn {
  border: 1px solid rgba(255,255,255,.36);
  border-radius: 18px;
  background: rgba(255,255,255,.10);
  color: #fff;
  padding: 7px 11px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}
main { padding: 14px 12px 22px; display: grid; gap: 12px; }
.hero {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 15px;
  border-radius: 12px;
  color: #174a82;
  background: #dcecff;
}
.hero-icon { width: 42px; height: 42px; flex: 0 0 42px; border-radius: 12px; background: #fff; display: grid; place-items: center; font-size: 22px; }
.hero strong { font-size: 14px; }
.hero p { margin: 4px 0 0; font-size: 12px; line-height: 1.55; opacity: .8; }
.card {
  padding: 16px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 7px 22px rgba(31,56,100,.07);
}
.field { display: grid; gap: 8px; font-size: 13px; font-weight: 600; }
.field small { color: #7d8ba2; font-weight: 400; }
.file-picker {
  margin-top: 16px;
  min-height: 142px;
  border: 1.5px dashed #9fb4d2;
  border-radius: 12px;
  background: #f8fbff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  color: #28588f;
}
.file-picker input { display: none; }
.file-picker .el-icon { font-size: 32px; }
.file-picker strong { max-width: 90%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.file-picker span { font-size: 11px; color: #8190a7; }
.status { margin: 14px 0 10px; display: flex; align-items: center; gap: 8px; font-size: 12px; color: #60708a; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #9aa6ba; }
.status.uploading .dot, .status.analyzing .dot { background: #e9a23b; animation: pulse 1.2s infinite; }
.status.done .dot { background: #1e9b63; }
.status.failed .dot { background: #cf493b; }
@keyframes pulse { 50% { opacity: .35; } }
.progress-wrap { margin-bottom: 12px; }
.upload-btn { width: 100%; height: 48px; border-radius: 10px; font-size: 15px; }
.result.done { border-left: 4px solid #1e9b63; }
.result.failed { border-left: 4px solid #cf493b; }
.result-title { font-size: 16px; font-weight: 700; }
.result p { font-size: 12.5px; color: #66758e; line-height: 1.65; }
.quality { display: flex; flex-wrap: wrap; gap: 7px; }
.quality span { padding: 5px 8px; border-radius: 8px; background: #f0f6ff; color: #32649c; font-size: 11px; }
.section-head { display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.section-head span { color: #8795aa; font-size: 11px; }
.recent-row { display: flex; justify-content: space-between; gap: 10px; padding: 12px 0; border-bottom: 1px solid #edf1f7; }
.recent-row:last-child { border-bottom: none; }
.recent-row strong { display: block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12.5px; }
.recent-row small { color: #8492a8; font-size: 10.5px; }
.state { align-self: center; padding: 4px 7px; border-radius: 9px; font-size: 10.5px; background: #edf1f7; }
.state.analyzed { color: #147c4f; background: #e8f7ef; }
.state.analyzing { color: #9a6712; background: #fff4db; }
.state.failed { color: #a4382e; background: #fdecea; }
.empty { padding: 24px 0; text-align: center; color: #9aa6b8; font-size: 12px; }
footer { padding: 0 20px 24px; text-align: center; color: #8a97aa; font-size: 10.5px; }
@media (min-width: 720px) {
  .mobile-page { max-width: 560px; margin: 0 auto; box-shadow: 0 0 40px rgba(31,56,100,.12); }
}
</style>
