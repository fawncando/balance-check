import { createRouter, createWebHistory } from 'vue-router'
import { auth, restore } from '../stores/auth'
import { canAccessRoles, isMobileClient } from '../utils/device'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/mobile', name: 'mobileUpload', component: () => import('../views/MobileUploadView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/workbench' },
      { path: 'workbench', name: 'workbench', component: () => import('../views/WorkbenchView.vue') },
      { path: 'records', name: 'records', component: () => import('../views/CalibrationsView.vue') },
      {
        path: 'calibrations/new',
        name: 'calibrationNew',
        component: () => import('../views/CalibrationFormView.vue'),
        meta: { roles: ['INSPECTOR', 'ADMIN'] }
      },
      { path: 'calibrations/:id', name: 'calibrationDetail', component: () => import('../views/CalibrationDetailView.vue') },
      { path: 'calibrations/:id/edit', name: 'calibrationEdit', component: () => import('../views/CalibrationFormView.vue') },
      { path: 'reviews', name: 'reviews', component: () => import('../views/ReviewView.vue') },
      { path: 'balances', name: 'balances', component: () => import('../views/BalancesView.vue') },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { roles: ['ADMIN'] }
      },
      {
        path: 'templates',
        name: 'templates',
        component: () => import('../views/TemplatesView.vue'),
        meta: { roles: ['ADMIN'] }
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('../views/LogsView.vue'),
        meta: { roles: ['ADMIN'] }
      }
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
  if (to.name === 'workbench' && isMobileClient()) {
    return { name: 'mobileUpload', replace: true }
  }
  if (!canAccessRoles(user.role, to.meta.roles)) {
    return { name: 'workbench', replace: true }
  }
  return true
})

export default router
