import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ArcoVue from '@arco-design/web-vue'; // 引入 Arco 插件
import App from './App.vue';
import router from './router';
import '@arco-design/web-vue/dist/arco.css';
import './assets/styles/base.css';

const app = createApp(App);

app.use(createPinia());
app.use(ArcoVue); // 注册插件
app.use(router);

app.mount('#app');
