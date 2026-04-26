<script setup lang="ts">
import { ref } from 'vue';
import { useUIStore } from '../stores/ui';
import IconUser from '@arco-design/web-vue/es/icon/icon-user';
import IconSettings from '@arco-design/web-vue/es/icon/icon-settings';
import IconThunderbolt from '@arco-design/web-vue/es/icon/icon-thunderbolt';
import IconNotification from '@arco-design/web-vue/es/icon/icon-notification';
import IconApps from '@arco-design/web-vue/es/icon/icon-apps';
import IconInfoCircle from '@arco-design/web-vue/es/icon/icon-info-circle';
import IconRobot from '@arco-design/web-vue/es/icon/icon-robot';
import AgentManager from '../components/settings/AgentManager.vue';

const uiStore = useUIStore();
const activeKey = ref('general');

const menuItems = [
  { key: 'account', title: '账号与存储', icon: IconUser },
  { key: 'general', title: '通用', icon: IconSettings },
  { key: 'agents', title: '智能体管理', icon: IconRobot },
  { key: 'shortcuts', title: '快捷键', icon: IconThunderbolt },
  { key: 'notifications', title: '通知', icon: IconNotification },
  { key: 'plugins', title: '插件', icon: IconApps },
  { key: 'about', title: '关于灵枢', icon: IconInfoCircle },
];

const fontSizeMarks = {
  1: '小',
  2: '标准',
  3: '',
  4: '',
  5: '大'
};

const appearanceOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
];

const languageOptions = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
];
</script>

<template>
  <div class="settings-container">
    <!-- Left Sidebar Menu -->
    <div class="settings-sidebar">
      <div 
        v-for="item in menuItems" 
        :key="item.key"
        class="menu-item"
        :class="{ active: activeKey === item.key }"
        @click="activeKey = item.key"
      >
        <component :is="item.icon" class="item-icon" />
        <span class="item-title">{{ item.title }}</span>
      </div>
    </div>

    <!-- Right Settings Content -->
    <div class="settings-content">
      <div v-show="activeKey === 'general'" class="settings-scroll-area">
        
        <!-- Language & Translation Section -->
        <div class="settings-group">
          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">语言</span>
            </div>
            <div class="row-action">
              <a-select :options="languageOptions" :style="{ width: '120px' }" defaultValue="zh-CN" size="small" />
            </div>
          </div>
          
          <div class="setting-row multi-line">
            <div class="row-label">
              <span class="main-text">将文字翻译为</span>
              <span class="sub-text">在微信聊天、网页及图片中使用翻译功能时，文字会被翻译为所选语言。</span>
            </div>
            <div class="row-action">
              <a-select :options="languageOptions" :style="{ width: '160px' }" defaultValue="zh-CN" size="small" />
            </div>
          </div>

          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">自动翻译聊天中收到的消息</span>
              <span class="sub-text">开启后，所有聊天收到的消息会被自动翻译</span>
            </div>
            <div class="row-action">
              <a-switch size="small" />
            </div>
          </div>
        </div>

        <!-- Appearance Section -->
        <div class="settings-group">
          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">外观</span>
            </div>
            <div class="row-action">
              <a-select 
                v-model="uiStore.theme" 
                :options="appearanceOptions" 
                :style="{ width: '120px' }" 
                @change="(val: any) => uiStore.setTheme(val)"
                size="small" 
              />
            </div>
          </div>
          
          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">字体大小</span>
            </div>
            <div class="row-action slider-container">
              <a-slider 
                v-model="uiStore.fontSize" 
                :min="1" 
                :max="5" 
                :step="1" 
                :marks="fontSizeMarks"
                @change="(val: any) => uiStore.setFontSize(val)"
                :style="{ width: '200px' }"
              />
            </div>
          </div>
        </div>

        <!-- System Settings Section -->
        <div class="settings-group">
          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">以只读的方式打开聊天中的文件</span>
              <span class="sub-text">开启后，可保护聊天中的文件不被修改。</span>
            </div>
            <div class="row-action">
              <a-switch default-checked size="small" />
            </div>
          </div>

          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">显示网络搜索历史</span>
            </div>
            <div class="row-action">
              <a-switch default-checked size="small" />
            </div>
          </div>

          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">聊天中的语音消息自动转成文字</span>
            </div>
            <div class="row-action">
              <a-switch size="small" />
            </div>
          </div>

          <div class="setting-row">
            <div class="row-label">
              <span class="main-text">使用系统默认浏览器打开第三方网页</span>
            </div>
            <div class="row-action">
              <a-switch size="small" />
            </div>
          </div>
        </div>

      </div>

      <div v-show="activeKey === 'agents'" class="settings-scroll-area">
        <AgentManager />
      </div>


      <div v-show="activeKey === 'about'" class="settings-scroll-area">
        <div class="about-content-inner">
          <div class="about-hero">
            <img src="/linger.png" class="about-logo" alt="Linger" />
            <h2 class="app-name">灵枢 · LingShu AI</h2>
            <p class="app-version">Version 0.1.0 (Alpha Build)</p>
            <p class="app-tagline">“在本地的代码旷野里，养育一个懂你的、能帮你的数字生命”</p>
          </div>

          <div class="about-sections">
            <div class="section-block">
              <h4 class="block-title">项目愿景</h4>
              <div class="vision-cards">
                <div class="vision-card">
                  <span class="v-title">灵</span>
                  <p>象征智能、情感感知与主动交互，承载长期记忆，懂你所思、记你所好，时刻陪伴。</p>
                </div>
                <div class="vision-card">
                  <span class="v-title">枢</span>
                  <p>意为智能体调度中枢，代表开放、兼容、可无限扩展的统御能力，掌控数字世界的入口。</p>
                </div>
              </div>
            </div>

            <div class="section-block">
              <h4 class="block-title">核心特性</h4>
              <div class="features-grid">
                <div class="feature-item">
                  <div class="f-icon">
                    <span class="material-symbols-outlined">psychology</span>
                  </div>
                  <div class="f-content">
                    <h5>长期记忆 (LTM)</h5>
                    <p>基于 Neo4j + pgvector 的多级架构，实现知识的持久留存与关联。</p>
                  </div>
                </div>
                <div class="feature-item">
                  <div class="f-icon">
                    <span class="material-symbols-outlined">hub</span>
                  </div>
                  <div class="f-content">
                    <h5>记忆可视化</h5>
                    <p>3D 银河图谱展示，让 AI 的思考过程与记忆脉络完全透明。</p>
                  </div>
                </div>
                <div class="feature-item">
                  <div class="f-icon">
                    <span class="material-symbols-outlined">theater_comedy</span>
                  </div>
                  <div class="f-content">
                    <h5>情感演化</h5>
                    <p>内置情感分析引擎，AI 的回复与行为将随用户情绪动态波动。</p>
                  </div>
                </div>
                <div class="feature-item">
                  <div class="f-icon">
                    <span class="material-symbols-outlined">extension</span>
                  </div>
                  <div class="f-content">
                    <h5>MCP 驱动</h5>
                    <p>支持 Model Context Protocol 规范，具备无限的外部工具调用能力。</p>
                  </div>
                </div>
                <div class="feature-item">
                  <div class="f-icon">
                    <span class="material-symbols-outlined">shield_person</span>
                  </div>
                  <div class="f-content">
                    <h5>隐私至上</h5>
                    <p>本地化部署架构，数据不出内网，私密生活不再是黑盒。</p>
                  </div>
                </div>
              </div>
            </div>

            <div class="about-footer">
              <p>© 2026 LingShu AI Project. Powered by LangChain4j.</p>
              <div class="footer-actions">
                <a-button type="text" size="mini">检查更新</a-button>
                <a-button type="text" size="mini">GitHub 仓库</a-button>
                <a-button type="text" size="mini">致谢名单</a-button>
              </div>
            </div>
          </div>
        </div>
      </div>


      <div v-show="!['general', 'agents', 'about'].includes(activeKey)" class="empty-detail">
        <p>该功能正在开发中...</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-container {
  display: flex;
  width: 100%;
  height: 100%;
  background-color: var(--bg-sidebar);
}

/* Sidebar Styling */
.settings-sidebar {
  width: 240px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background-color: var(--bg-conversation-list);
  border-right: 1px solid var(--border-color);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-primary);
}

.menu-item:hover {
  background-color: var(--bg-hover);
}

.menu-item.active {
  background-color: var(--bg-selected);
}

.item-icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.item-title {
  font-size: 14px;
}

/* Content Styling */
.settings-content {
  flex: 1;
  background-color: var(--bg-chat-window);
  overflow: hidden;
  position: relative;
}

.settings-scroll-area {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 30px 40px; /* Padding moved here */
}

/* Custom Scrollbar */
.settings-scroll-area::-webkit-scrollbar {
  width: 5px;
}

.settings-scroll-area::-webkit-scrollbar-track {
  background: transparent;
}

.settings-scroll-area::-webkit-scrollbar-thumb {
  background: rgba(var(--primary-color-rgb, 0, 0, 0), 0.1);
  border-radius: 10px;
  transition: background 0.3s;
}

.settings-scroll-area:hover::-webkit-scrollbar-thumb {
  background: rgba(var(--primary-color-rgb, 0, 0, 0), 0.25);
}

.settings-group {
  background-color: var(--bg-input);
  border-radius: 12px;
  padding: 4px 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-row.multi-line {
  align-items: flex-start;
}

.row-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 70%;
}

.main-text {
  font-size: 14px;
  color: var(--text-primary);
}

.sub-text {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.4;
}

.row-action {
  display: flex;
  align-items: center;
}

.slider-container {
  padding-top: 10px;
  min-width: 200px;
}

.empty-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
}

/* About Page Styling */
.about-content-inner {
  display: flex;
  flex-direction: column;
  gap: 32px;
  max-width: 800px;
  margin: 0 auto;
  padding: 30px 40px 60px;
}

.about-hero {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.about-logo {
  width: 96px;
  height: 96px;
  border-radius: 20px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
  margin-bottom: 8px;
}

.app-name {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.app-version {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

.app-tagline {
  font-size: 14px;
  color: var(--color-primary);
  font-style: italic;
  margin-top: 4px;
}

.about-sections {
  display: flex;
  flex-direction: column;
  gap: 48px;
  margin-top: 20px;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.block-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  padding-left: 12px;
  border-left: 4px solid var(--color-primary);
}

.vision-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.vision-card {
  background-color: var(--bg-input);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
}

.v-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary);
  display: block;
  margin-bottom: 8px;
}

.vision-card p {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin: 0;
}

.features-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.feature-item {
  display: flex;
  gap: 20px;
  background-color: var(--bg-input);
  padding: 20px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  transition: all 0.2s;
}

.feature-item:hover {
  border-color: var(--color-primary-light-3);
  transform: translateY(-2px);
}

.f-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background-color: var(--color-primary-light-1);
  color: var(--color-primary);
  border-radius: 10px;
  flex-shrink: 0;
}

.f-icon .material-symbols-outlined {
  font-size: 24px;
}

.f-content h5 {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 6px 0;
  color: var(--text-primary);
}

.f-content p {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
  line-height: 1.6;
}

.about-footer {
  margin-top: 20px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
}

.footer-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 8px;
}

</style>

