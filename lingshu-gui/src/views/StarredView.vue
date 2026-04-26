<template>
  <div class="starred-view">
    <div class="empty-state">
      <svg class="empty-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
        <path d="M784 112L536 480l-248-368-176 128 312 464-312 464 176 128 248-368 248 368 176-128-312-464 312-464z" fill="#E8E8E8"/>
      </svg>
      <h3>收藏</h3>
      <p>你收藏的消息和知识点会出现在这里</p>
    </div>
  </div>
</template>

<style scoped>
.starred-view {
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
