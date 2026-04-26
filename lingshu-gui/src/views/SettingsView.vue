<template>
  <div class="settings-view">
    <div class="empty-state">
      <svg class="empty-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
        <path d="M896 512c0-52.5-20.2-100.4-53.4-136.6l-77.2 77.2c13.4 17.4 21.4 39.2 21.4 62.8 0 53-43 96-96 96s-96-43-96-96c0-23.6 8-45.4 21.4-62.8l-77.2-77.2C505.8 411.6 485.6 459.5 485.6 512c0 106 86 192 192 192s192-86 192-192z" fill="#E8E8E8"/>
        <path d="M512 128c-212.1 0-384 171.9-384 384s171.9 384 384 384 384-171.9 384-384-171.9-384-384-384z" fill="#D9D9D9"/>
      </svg>
      <h3>设置</h3>
      <p>配置你的应用偏好</p>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
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
