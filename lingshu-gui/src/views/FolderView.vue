<template>
  <div class="folder-view">
    <div class="empty-state">
      <svg class="empty-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
        <path d="M928 320H96c-17.7 0-32 14.3-32 32v512c0 17.7 14.3 32 32 32h832c17.7 0 32-14.3 32-32V352c0-17.7-14.3-32-32-32z" fill="#E8E8E8"/>
        <path d="M256 192h128c17.7 0 32 14.3 32 32v96H128V224c0-17.7 14.3-32 32-32h96z" fill="#D9D9D9"/>
      </svg>
      <h3>文件夹</h3>
      <p>管理你的知识库文件</p>
    </div>
  </div>
</template>

<style scoped>
.folder-view {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-chat-window);
}
.empty-state {
  text-align: center;
  color: var(--text-tertiary);
}
.empty-icon {
  width: 120px;
  height: 120px;
  margin-bottom: 16px;
  opacity: 0.4;
}
h3 {
  font-size: var(--font-size-lg);
  font-weight: 500;
  margin: 0 0 8px;
  color: var(--text-secondary);
}
p {
  font-size: var(--font-size-base);
  margin: 0;
  color: var(--text-placeholder);
}
</style>
