<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../stores/auth'

const username = ref('liyi')
const password = ref('123456')
const loading = ref(false)
const router = useRouter()
const route = useRoute()

async function submit() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await login(username.value.trim(), password.value)
    router.replace(route.query.redirect || '/workbench')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo">天平</div>
      <h1>天平校验视频分析系统</h1>
      <p class="sub">电子天平日常校验记录、判定与复核平台</p>

      <el-form @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="username" placeholder="请输入工号或用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" placeholder="请输入密码" size="large" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="demo">
        <div>演示账号（口令均为 123456）</div>
        <div>liyi 检验人员 · qiyi 复核确认人员 · admin 管理员</div>
      </div>
      <div class="foot">
        本系统部署于实验室内网，与外网物理隔离，运行期间不访问外部服务。
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef3fb 0%, #e3ecf9 48%, #dfe8f6 100%);
}
.login-card {
  width: 430px;
  background: #fff;
  border: 1px solid #d7dfec;
  border-radius: 10px;
  padding: 32px 34px 24px;
  box-shadow: 0 12px 28px rgba(31, 56, 100, .10);
}
.logo {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: #163a69;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
}
h1 { font-size: 21px; margin: 18px 0 6px; }
.sub { font-size: 12.5px; color: #5c6b87; margin: 0 0 24px; }
.login-btn { width: 100%; margin-top: 4px; }
.demo {
  margin-top: 18px;
  padding: 10px 12px;
  background: #f5f8fd;
  border-radius: 6px;
  font-size: 12px;
  color: #5c6b87;
  line-height: 1.8;
}
.foot {
  margin-top: 14px;
  font-size: 11.5px;
  color: #93a0b5;
  line-height: 1.8;
}
</style>
