<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  DataAnalysis, Document, EditPen, Files, Monitor, SwitchButton, Tickets, View
} from '@element-plus/icons-vue'
import { auth, logout, roleLabel } from '../stores/auth'

const route = useRoute()
const router = useRouter()

const groups = [
  {
    title: '校验业务',
    items: [
      { index: '/workbench', title: '检验工作台', icon: Monitor },
      { index: '/records', title: '校验记录查询', icon: Document },
      { index: '/calibrations/new', title: '新建校验记录', icon: EditPen, roles: ['INSPECTOR', 'ADMIN'] },
      { index: '/reviews', title: '复核工作台', icon: View }
    ]
  },
  {
    title: '基础数据',
    items: [
      { index: '/balances', title: '天平清单', icon: Files },
      { index: '/templates', title: '记录表模板', icon: Tickets, roles: ['ADMIN'] },
      { index: '/logs', title: '操作日志', icon: DataAnalysis, roles: ['ADMIN'] }
    ]
  }
]

const active = computed(() => route.path)
const user = computed(() => auth.user)
const visibleGroups = computed(() => groups
  .map((group) => ({
    title: group.title,
    items: group.items.filter((item) => !item.roles || item.roles.includes(user.value?.role))
  }))
  .filter((group) => group.items.length > 0))

async function onLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '退出登录', { type: 'warning' })
  await logout()
  router.replace('/login')
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        天平校验视频分析系统
        <span class="badge">内网离线版 V0.1</span>
      </div>
      <div class="user" v-if="user">
        <span class="dot"></span>
        <span>{{ user.displayName }}（{{ roleLabel[user.role] }}）</span>
        <el-button link class="logout" :icon="SwitchButton" @click="onLogout">退出</el-button>
      </div>
    </header>
    <div class="body">
      <aside class="side">
        <template v-for="group in visibleGroups" :key="group.title">
          <div class="group">{{ group.title }}</div>
          <el-menu :default-active="active" router class="menu">
            <el-menu-item v-for="item in group.items" :key="item.index" :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-menu>
        </template>
        <div class="version">
          批次 7 · 完善优化<br />
          清单维护 / 记录表模板 / 操作日志
        </div>
      </aside>
      <main class="main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell { min-height: 100%; display: flex; flex-direction: column; }
.topbar {
  height: 58px;
  background: #163a69;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  flex: 0 0 58px;
}
.brand { display: flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 700; letter-spacing: .4px; }
.badge {
  font-size: 11px;
  font-weight: 400;
  border: 1px solid rgba(255, 255, 255, .5);
  padding: 2px 7px;
  border-radius: 10px;
  letter-spacing: 0;
}
.user { font-size: 13px; display: flex; align-items: center; gap: 10px; }
.user .dot { width: 8px; height: 8px; border-radius: 50%; background: #6fd39a; }
.logout { color: #dbe6f7; }
.body { flex: 1; display: flex; min-height: 0; }
.side {
  width: 208px;
  flex: 0 0 208px;
  background: #fff;
  border-right: 1px solid #dfe5ee;
  padding: 8px 0 16px;
  display: flex;
  flex-direction: column;
}
.group { font-size: 11.5px; color: #98a4b8; padding: 14px 20px 6px; letter-spacing: 1px; }
.menu { border-right: none; }
.menu :deep(.el-menu-item) { height: 42px; font-size: 13.5px; color: #5c6b87; }
.menu :deep(.el-menu-item.is-active) { color: #1f4e8c; background: #eef3fb; font-weight: 700; }
.version { margin-top: auto; padding: 12px 20px 0; font-size: 11px; color: #a6b1c3; line-height: 1.7; }
.main { flex: 1; padding: 18px 22px 26px; min-width: 0; }
</style>
