<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Postcard, Printer } from '@element-plus/icons-vue'
import http from '../api/http'
import { auth } from '../stores/auth'

const users = ref([])
const loading = ref(false)
const editing = reactive({ visible: false, row: null, cardNo: '', saving: false })
const qr = reactive({ visible: false, row: null, version: Date.now() })

const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const roleLabel = {
  ADMIN: '管理员',
  INSPECTOR: '检验人员',
  REVIEWER: '复核确认人员'
}

onMounted(load)

async function load() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    users.value = await http.get('/users')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  editing.row = row
  editing.cardNo = row.cardNo || ''
  editing.visible = true
}

async function saveCard() {
  const value = editing.cardNo.trim().toUpperCase()
  if (!/^[A-Z0-9_-]{2,20}$/.test(value)) {
    ElMessage.warning('卡号只能包含 2 至 20 位字母、数字、下划线或短横线')
    return
  }
  editing.saving = true
  try {
    await http.put(`/users/${editing.row.id}/card`, { cardNo: value })
    ElMessage.success('人员卡号已保存')
    editing.visible = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    editing.saving = false
  }
}

async function clearCard(row) {
  try {
    await ElMessageBox.confirm(
      `确定解绑 ${row.displayName} 的人员卡 ${row.cardNo} 吗？解绑后将无法通过人员卡身份核验。`,
      '解绑人员卡',
      { type: 'warning', confirmButtonText: '确认解绑' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/users/${row.id}/card`)
    ElMessage.success('人员卡已解绑')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function showQr(row) {
  qr.row = row
  qr.version = Date.now()
  qr.visible = true
}

function qrUrl(row) {
  return `/api/users/${row.id}/card/qr?v=${qr.version}`
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function printCard(row) {
  const popup = window.open('', '_blank', 'width=560,height=760')
  if (!popup) {
    ElMessage.warning('浏览器阻止了打印窗口，请允许弹出窗口')
    return
  }
  const qrImage = new URL(qrUrl(row), window.location.origin).href
  popup.document.write(`
    <!doctype html>
    <html lang="zh-CN">
    <head>
      <meta charset="utf-8">
      <title>人员卡 ${escapeHtml(row.cardNo)}</title>
      <style>
        @page { size: A6 portrait; margin: 8mm; }
        body { font-family: "Microsoft YaHei", sans-serif; text-align: center; color: #1f2a44; }
        h1 { font-size: 18px; margin: 0 0 4px; }
        p { margin: 4px 0; color: #5c6b87; }
        img { width: 240px; height: 240px; margin: 18px auto; display: block; }
        .card { border: 2px solid #163a69; border-radius: 12px; padding: 22px; }
        .no { font: 700 22px Consolas, monospace; letter-spacing: 2px; margin-top: 8px; }
      </style>
    </head>
    <body>
      <div class="card">
        <h1>天平校验人员卡</h1>
        <p>${escapeHtml(row.displayName)}（${escapeHtml(row.username)}）</p>
        <img src="${qrImage}" onload="window.print()">
        <div class="no">${escapeHtml(row.cardNo)}</div>
      </div>
    </body>
    </html>
  `)
  popup.document.close()
}
</script>

<template>
  <div v-loading="loading">
    <div class="head">
      <h1 class="page-title">人员与卡号</h1>
      <span class="note">管理员维护每人唯一二维码卡号</span>
    </div>

    <div v-if="!isAdmin" class="card">
      <h2>无权限</h2>
      <div class="note">人员卡维护只对管理员开放。</div>
    </div>

    <template v-else>
      <div class="card intro">
        <el-icon><Postcard /></el-icon>
        <div>
          <strong>人员卡二维码内容即卡号</strong>
          <p>视频中的二维码必须与当前登录账号的卡号一致。修改卡号后应立即作废旧卡。</p>
        </div>
      </div>

      <div class="card">
        <el-table :data="users" size="small" empty-text="没有人员数据">
          <el-table-column prop="username" label="登录账号" width="120" />
          <el-table-column prop="displayName" label="姓名" width="110" />
          <el-table-column label="角色" width="130">
            <template #default="{ row }">{{ roleLabel[row.role] || row.role }}</template>
          </el-table-column>
          <el-table-column label="人员卡号" min-width="150">
            <template #default="{ row }">
              <code v-if="row.cardNo">{{ row.cardNo }}</code>
              <el-tag v-else size="small" type="info">未绑定</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">设置卡号</el-button>
              <el-button v-if="row.cardNo" link type="success" @click="showQr(row)">查看二维码</el-button>
              <el-button v-if="row.cardNo" link type="primary" @click="printCard(row)">打印</el-button>
              <el-button v-if="row.cardNo" link type="danger" @click="clearCard(row)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-dialog v-model="editing.visible" title="设置人员卡号" width="430px">
      <div v-if="editing.row" class="edit-body">
        <p>{{ editing.row.displayName }}（{{ editing.row.username }}）</p>
        <el-input
          v-model="editing.cardNo"
          maxlength="20"
          show-word-limit
          placeholder="例如 RY-001"
          @keyup.enter="saveCard"
        />
        <div class="note">只能使用字母、数字、下划线或短横线，卡号必须全局唯一。</div>
      </div>
      <template #footer>
        <el-button @click="editing.visible = false">取消</el-button>
        <el-button type="primary" :loading="editing.saving" @click="saveCard">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qr.visible" title="人员卡二维码" width="440px" class="qr-dialog">
      <div v-if="qr.row" class="qr-card">
        <h3>{{ qr.row.displayName }}</h3>
        <div class="note">{{ qr.row.username }} · {{ roleLabel[qr.row.role] }}</div>
        <img :src="qrUrl(qr.row)" :alt="`人员卡二维码 ${qr.row.cardNo}`" />
        <code>{{ qr.row.cardNo }}</code>
      </div>
      <template #footer>
        <el-button :icon="Printer" @click="printCard(qr.row)">打印卡片</el-button>
        <el-button :icon="Download" type="primary" tag="a" :href="qrUrl(qr.row)" :download="`${qr.row.cardNo}.png`">
          下载二维码
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: baseline; gap: 14px; margin-bottom: 14px; }
.intro { display: flex; align-items: center; gap: 14px; background: #f3f8ff; }
.intro .el-icon { font-size: 34px; color: #1f5c9d; }
.intro strong { font-size: 14px; }
.intro p { margin: 4px 0 0; font-size: 12px; color: #6c7b92; }
code { color: #174d85; background: #edf4fd; border-radius: 5px; padding: 3px 7px; }
.edit-body { display: grid; gap: 12px; }
.edit-body p { margin: 0; font-weight: 600; }
.qr-card { text-align: center; }
.qr-card h3 { margin: 0 0 4px; font-size: 19px; }
.qr-card img { width: 260px; height: 260px; display: block; margin: 18px auto; border: 1px solid #e0e7f1; }
.qr-card code { font-size: 18px; letter-spacing: 2px; }
</style>
