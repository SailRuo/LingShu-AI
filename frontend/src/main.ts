import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import './style.css'

// 通用字体
import 'vfonts/Inter.css'
import 'vfonts/FiraCode.css'

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
