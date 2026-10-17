<script setup>
import { useRouter } from 'vue-router'

defineProps({
  title: { type: String, required: true },
  batch: { type: String, required: true }
})

const router = useRouter()
</script>

<template>
  <div>
    <h1 class="page-title">{{ title }}</h1>
    <div class="card">
      <h2>该模块将在{{ batch }}接入</h2>
      <p class="note">
        批次 3 的交付范围是登录与权限、视频上传与存储、天平清单读取。
        本页面已经占位，后续批次会在不改动路由与菜单的前提下补齐功能。
      </p>
      <el-button type="primary" plain @click="router.push('/workbench')">返回检验工作台</el-button>
    </div>
  </div>
</template>
