import { createRouter, createWebHistory } from 'vue-router'
import { auth, restore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/workbench' },
      { path: 'workbench', name: 'workbench', component: () => import('../views/WorkbenchView.vue') },
      { path: 'records', name: 'records', component: () => import('../views/PlaceholderView.vue'), props: { title: '校验记录查询', batch: '第 5、6 批次' } },
      { path: 'reviews', name: 'reviews', component: () => import('../views/PlaceholderView.vue'), props: { title: '待复核记录', batch: '第 6 批次' } },
      { path: 'balances', name: 'balances', component: () => import('../views/BalancesView.vue') },
      { path: 'logs', name: 'logs', component: () => import('../views/PlaceholderView.vue'), props: { title: '操作日志', batch: '第 7 批次' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.name === 'login') return true
  const user = auth.user || (await restore())
  if (!user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
