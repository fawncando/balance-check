<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { auth } from '../stores/auth'

const router = useRouter()
const balances = ref([])
const videos = ref([])
const balanceId = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const analyzingId = ref(null)
const progress = ref(0)
const loading = ref(false)

const statusMap = {
  UPLOADED: { text: '已上传', type: 'info' },
  ANALYZING: { text: '识别中', type: 'warning' },
  ANALYZED: { text: '待确认', type: 'success' },
  FAILED: { text: '识别失败', type: 'danger' }
}

const balanceOptions = computed(() =>
  balances.value.map((b) => ({
    id: b.id,
    label: `${b.code}（${b.uniqueNo}）`
  }))
)

onMounted(async () => {
  await Promise.all([loadBalances(), loadVideos()])
})

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

async function loadVideos() {
  loading.value = true
  try {
    videos.value = await http.get('/videos')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
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
  if (raw.size > 500 * 1024 * 1024) {
    ElMessage.error('单个视频不能超过 500MB')
    return
  }
  selectedFile.value = raw
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
    const analyzed = await http.post(`/videos/${uploaded.id}/analyze`)
    selectedFile.value = null
    progress.value = 0
    await loadVideos()
    if (analyzed.status === 'ANALYZED' && analyzed.calibrationId) {
      ElMessage.success('识别完成，已生成校验草稿')
      router.push(`/calibrations/${analyzed.calibrationId}/edit`)
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
    const analyzed = await http.post(`/videos/${row.id}/analyze`)
    await loadVideos()
    if (analyzed.status !== 'ANALYZED' || !analyzed.calibrationId) {
      ElMessage.error(analyzed.message || '识别失败')
      return
    }
    ElMessage.success('识别完成，已生成校验草稿')
    router.push(`/calibrations/${analyzed.calibrationId}/edit`)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    analyzingId.value = null
  }
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
</script>

<template>
  <div>
    <h1 class="page-title">检验工作台</h1>

    <div class="card">
      <h2><span class="no">1</span>导入校验视频<span class="hint">视频由检验人员按称量 SOP 拍摄完成后在手机端上传，系统从视频文件开始处理</span></h2>

      <div class="pick">
        <span class="label">本次校验的天平</span>
        <el-select v-model="balanceId" placeholder="请选择天平" style="width: 260px">
          <el-option v-for="item in balanceOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <span class="note">天平信息来自天平清单，识别阶段会据此带出量程、分度值与砝码组。</span>
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
          <div class="el-upload__tip">支持 mp4、mov、m4v、avi、mkv，单个文件不超过 500MB</div>
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
        <el-table-column prop="originalName" label="视频文件" min-width="260" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column prop="balanceCode" label="天平" width="90" />
        <el-table-column prop="uploaderName" label="上传人" width="100" />
        <el-table-column prop="uploadedAt" label="上传时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UPLOADED' && row.uploaderName === auth.user?.displayName"
              link
              type="primary"
              :loading="analyzingId === row.id"
              @click="analyzeVideo(row)"
            >开始识别</el-button>
            <el-button
              v-else-if="row.status === 'FAILED' && row.uploaderName === auth.user?.displayName"
              link
              type="primary"
              :loading="analyzingId === row.id"
              @click="analyzeVideo(row)"
            >重新识别</el-button>
            <el-button
              v-else-if="row.status === 'ANALYZED' && row.calibrationId"
              link
              type="primary"
              @click="router.push(`/calibrations/${row.calibrationId}/edit`)"
            >确认结果</el-button>
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
</style>
