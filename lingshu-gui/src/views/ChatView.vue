<script setup lang="ts">
import ConversationList from '../components/chat/ConversationList.vue';
import ChatWindow from '../components/chat/ChatWindow.vue';
</script>

<template>
  <div class="chat-view">
    <ConversationList />
    <ChatWindow />
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
</style>
