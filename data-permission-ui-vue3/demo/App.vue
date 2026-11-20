<template>
  <div class="demo-shell">
    <header class="demo-header">
      <div>
        <h1>数据权限管理</h1>
        <p>Vue 3 + Element Plus · 本地 Mock 数据（仅用于演示）</p>
      </div>
      <el-tag type="info" effect="plain">Demo</el-tag>
    </header>
    <data-permission-page />
  </div>
</template>

<script setup>
import DataPermissionPage from '../src/views/system/dataPermission/index.vue'
</script>
