<script setup lang="ts">
import { onMounted } from 'vue';
import { useUIStore } from './stores/ui';

const uiStore = useUIStore();

onMounted(() => {
  uiStore.setTheme(uiStore.theme);
});
</script>

<template>
  <router-view />
</template>

<style>
/* 全屏基础设置 */
html, body, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
</style>
