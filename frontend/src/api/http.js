import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 600000,
  withCredentials: true
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        redirectToLogin(response.config?.url)
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      redirectToLogin(error.config?.url)
    }
    const status = error.response?.status
    let message = error.response?.data?.message
    if (!message && status === 403) {
      message = '没有权限执行该操作，请确认登录角色或访问来源'
    }
    message = message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  }
)

/** 分页列表请求：返回 { items, total }，总数来自 X-Total-Count 响应头。 */
export async function getPage(url, config = {}) {
  try {
    const response = await axios.get('/api' + url, {
      withCredentials: true,
      timeout: 600000,
      ...config
    })
    const body = response.data
    const headerTotal = Number(response.headers?.['x-total-count'])
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        const items = body.data || []
        return { items, total: Number.isFinite(headerTotal) ? headerTotal : items.length }
      }
      if (body.code === 401) {
        redirectToLogin(url)
      }
      throw new Error(body.message || '请求失败')
    }
    return { items: body || [], total: Number.isFinite(headerTotal) ? headerTotal : 0 }
  } catch (error) {
    if (error instanceof Error && !error.response) {
      throw error
    }
    if (error.response && error.response.status === 401) {
      redirectToLogin(url)
    }
    const status = error.response?.status
    let message = error.response?.data?.message
    if (!message && status === 403) {
      message = '没有权限执行该操作，请确认登录角色或访问来源'
    }
    throw new Error(message || error.message || '网络异常')
  }
}

export default http

function redirectToLogin(url = '') {
  if (!url.includes('/auth/login')) {
    window.location.href = '/login'
  }
}
