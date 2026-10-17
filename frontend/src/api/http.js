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
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  }
)

export default http

function redirectToLogin(url = '') {
  if (!url.includes('/auth/login')) {
    window.location.href = '/login'
  }
}
