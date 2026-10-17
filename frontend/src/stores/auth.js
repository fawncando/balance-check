import { reactive } from 'vue'
import http from '../api/http'

export const auth = reactive({
  user: null,
  loading: false
})

export async function restore() {
  if (auth.user) return auth.user
  auth.loading = true
  try {
    auth.user = await http.get('/auth/me')
  } catch {
    auth.user = null
  } finally {
    auth.loading = false
  }
  return auth.user
}

export async function login(username, password) {
  auth.user = await http.post('/auth/login', { username, password })
  return auth.user
}

export async function logout() {
  try {
    await http.post('/auth/logout')
  } finally {
    auth.user = null
  }
}

export const roleLabel = {
  ADMIN: '管理员',
  INSPECTOR: '检验人员',
  REVIEWER: '复核确认人员'
}
