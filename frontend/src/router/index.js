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
      { path: 'records', name: 'records', component: () => import('../views/CalibrationsView.vue') },
      { path: 'calibrations/new', name: 'calibrationNew', component: () => import('../views/CalibrationFormView.vue') },
      { path: 'calibrations/:id', name: 'calibrationDetail', component: () => import('../views/CalibrationDetailView.vue') },
      { path: 'calibrations/:id/edit', name: 'calibrationEdit', component: () => import('../views/CalibrationFormView.vue') },
      { path: 'reviews', name: 'reviews', component: () => import('../views/ReviewView.vue') },
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
