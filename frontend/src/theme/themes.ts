import type { GlobalThemeOverrides } from 'naive-ui'
import type { ThemeConfig } from '@/types'
import { Sun, Sparkles, Cpu, Moon } from 'lucide-vue-next'

export const polarLight: ThemeConfig = {
  key: 'polarLight',
  label: '极地白',
  icon: Sun,
  isDark: false,
  cssVars: {
    '--color-background': '#f1f5f9',
    '--color-surface': 'rgba(255, 255, 255, 0.95)',
    '--color-surface-elevated': 'rgba(255, 255, 255, 1)',
    '--color-primary': '#0d9488',
    '--color-primary-dim': 'rgba(13, 148, 136, 0.12)',
    '--color-accent': '#c2410c',
    '--color-text': '#020617',
    '--color-text-dim': '#475569',
    '--color-text-inverse': '#ffffff',
    '--color-outline': 'rgba(0, 0, 0, 0.12)',
    '--color-success': '#059669',
    '--color-warning': '#b45309',
    '--color-error': '#b91c1c',
    '--color-glass-bg': 'rgba(255, 255, 255, 0.4)',
    '--color-glass-border': 'rgba(0, 0, 0, 0.12)',
    '--color-bg-mesh-1': '#f1f5f9',
    '--color-bg-mesh-2': '#cbd5e1',
    '--color-bg-mesh-3': '#99f6e4',
    '--color-bg-mesh-4': '#f1f5f9',
    // 气泡组件 - 增强视觉层次（毛玻璃效果）
    '--color-bubble-ai-bg': 'rgba(13, 148, 136, 0.08)', // 极淡
    '--color-bubble-ai-border': 'rgba(13, 148, 136, 0.18)', // 细边
    '--color-bubble-user-bg': 'rgba(255, 255, 255, 0.75)', // 毛玻璃白
    '--color-bubble-user-border': '#e0f2fe', // 浅蓝边
    '--color-pulse-core': 'rgba(13, 148, 136, 0.2)',
    '--color-pulse-ring': '#0d9488',
    '--color-node-user': '#0d9488',
    '--color-node-fact': '#c2410c',
    '--color-edge': 'rgba(13, 148, 136, 0.4)',
    // 发光效果 - 极简主义（弱光晕）
    '--color-glow': 'rgba(13, 148, 136, 0.35)', // 极地白 - 弱光晕
  }
}

export const cyberPurple: ThemeConfig = {
  key: 'cyberPurple',
  label: '星空',
  icon: Sparkles,
  isDark: true,
  cssVars: {
    // 核心颜色 - 纯黑宇宙背景 + 银白色主色
    '--color-background': '#000000', // 纯黑宇宙背景
    '--color-surface': 'rgba(10, 10, 20, 0.85)', // 黑表面 - 半透明
    '--color-surface-elevated': 'rgba(20, 20, 35, 0.9)', // 层次稍亮
    '--color-primary': '#e2e8f0', // 银白色 - 如恒星般明亮
    '--color-primary-dim': 'rgba(226, 232, 240, 0.15)', // 星光光晕
    '--color-accent': '#94a3b8', // 银灰色强调
    
    // 文字系统
    '--color-text': '#f1f5f9', // 纯白
    '--color-text-dim': '#cbd5e1', // 银灰次要文字
    '--color-text-inverse': '#020617',
    
    // 边框和轮廓
    '--color-outline': 'rgba(226, 232, 240, 0.25)', // 银色边框
    
    // 状态色
    '--color-success': '#4ade80',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    
    // 玻璃态效果
    '--color-glass-bg': 'rgba(10, 10, 20, 0.4)', // 黑玻璃
    '--color-glass-border': 'rgba(226, 232, 240, 0.2)',
    
    // Mesh 渐变背景 - 宇宙基调
    '--color-bg-mesh-1': '#000000',
    '--color-bg-mesh-2': '#0f172a', // 深蓝黑
    '--color-bg-mesh-3': '#1e293b', // 深灰蓝
    '--color-bg-mesh-4': '#000000',
    
    // 气泡组件 - 增强视觉层次（毛玻璃效果）
    '--color-bubble-ai-bg': 'rgba(226, 232, 240, 0.08)', // 更透明
    '--color-bubble-ai-border': 'rgba(226, 232, 240, 0.2)', // 细边
    '--color-bubble-user-bg': 'rgba(30, 30, 50, 0.65)', // 毛玻璃深空灰
    '--color-bubble-user-border': 'rgba(226, 232, 240, 0.3)', // 深边
    
    // 脉冲效果
    '--color-pulse-core': 'rgba(226, 232, 240, 0.4)',
    '--color-pulse-ring': '#e2e8f0',
    
    // 知识图谱节点
    '--color-node-user': '#e2e8f0',
    '--color-node-fact': '#cbd5e1',
    '--color-edge': 'rgba(226, 232, 240, 0.45)',
    
    // 发光效果 - 星芒感（强光晕）
    '--color-glow': 'rgba(226, 232, 240, 0.55)', // 星空 - 银白光晕
  }
}

export const deepTechFuturistic: ThemeConfig = {
  key: 'deepTechFuturistic',
  label: '霓虹绿境',
  icon: Cpu,
  isDark: true,
  cssVars: {
    // 核心颜色 - 深色背景 + 亮主色
    '--color-background': '#031f1a', // 深绿背景 - 降低亮度
    '--color-surface': 'rgba(5, 45, 35, 0.9)', // 深色表面 - 降低亮度
    '--color-surface-elevated': 'rgba(10, 70, 55, 0.92)', // 层次稍亮
    '--color-primary': '#34d399', // 亮绿色 - 保持醒目
    '--color-primary-dim': 'rgba(52, 211, 153, 0.15)', // 适度光晕
    '--color-accent': '#10b981', // 绿色强调
    
    // 文字系统
    '--color-text': '#ecfdf5', // 浅绿白
    '--color-text-dim': '#6ee7b7', // 浅绿次要文字
    '--color-text-inverse': '#053830',
    
    // 边框和轮廓
    '--color-outline': 'rgba(52, 211, 153, 0.35)', // 适度可见
    
    // 状态色
    '--color-success': '#34d399',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    
    // 玻璃态效果
    '--color-glass-bg': 'rgba(5, 45, 35, 0.45)', // 深绿玻璃
    '--color-glass-border': 'rgba(52, 211, 153, 0.28)',
    
    // Mesh 渐变背景 - 深色基调
    '--color-bg-mesh-1': '#031f1a',
    '--color-bg-mesh-2': '#0369a1', // 深蓝绿
    '--color-bg-mesh-3': '#024838', // 深绿色
    '--color-bg-mesh-4': '#031f1a',
    
    // 气泡组件 - 增强视觉层次（毛玻璃效果）
    '--color-bubble-ai-bg': 'rgba(52, 211, 153, 0.12)', // 更透明
    '--color-bubble-ai-border': 'rgba(52, 211, 153, 0.28)', // 更细
    '--color-bubble-user-bg': 'rgba(5, 45, 35, 0.70)', // 毛玻璃深绿
    '--color-bubble-user-border': 'rgba(52, 211, 153, 0.45)', // 半透明深边
    
    // 脉冲效果
    '--color-pulse-core': 'rgba(52, 211, 153, 0.45)',
    '--color-pulse-ring': '#34d399',
    
    // 知识图谱节点
    '--color-node-user': '#34d399',
    '--color-node-fact': '#10b981',
    '--color-edge': 'rgba(52, 211, 153, 0.55)',
    
    // 发光效果 - 科技光效（中光晕）
    '--color-glow': 'rgba(52, 211, 153, 0.55)', // 科技感 - 中光晕
  }
}

export const midnightBlue: ThemeConfig = {
  key: 'midnightBlue',
  label: '午夜蓝',
  icon: Moon,
  isDark: true,
  cssVars: {
    // 核心颜色 - 深蓝黑背景 + 天蓝色主色
    '--color-background': '#0a0f1c', // 深蓝黑
    '--color-surface': 'rgba(15, 30, 60, 0.9)', // 深蓝表面
    '--color-surface-elevated': 'rgba(20, 50, 90, 0.92)', // 层次稍亮
    '--color-primary': '#38bdf8', // 天蓝色
    '--color-primary-dim': 'rgba(56, 189, 248, 0.15)', // 适度光晕
    '--color-accent': '#818cf8', // 靛蓝色
    
    // 文字系统
    '--color-text': '#f0f9ff', // 浅蓝白
    '--color-text-dim': '#7dd3fc', // 浅蓝次要文字
    '--color-text-inverse': '#0a0f1c',
    
    // 边框和轮廓
    '--color-outline': 'rgba(56, 189, 248, 0.3)', // 适度可见
    
    // 状态色
    '--color-success': '#4ade80',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    
    // 玻璃态效果
    '--color-glass-bg': 'rgba(15, 30, 60, 0.45)', // 深蓝玻璃
    '--color-glass-border': 'rgba(56, 189, 248, 0.25)',
    
    // Mesh 渐变背景 - 深蓝基调
    '--color-bg-mesh-1': '#0a0f1c',
    '--color-bg-mesh-2': '#1e3a5f', // 深蓝色
    '--color-bg-mesh-3': '#0f2744', // 暗蓝色
    '--color-bg-mesh-4': '#0a0f1c',
    
    // 气泡组件 - 清晰层次（毛玻璃效果）
    '--color-bubble-ai-bg': 'rgba(56, 189, 248, 0.12)', // 更透明
    '--color-bubble-ai-border': 'rgba(56, 189, 248, 0.25)', // 细边
    '--color-bubble-user-bg': 'rgba(15, 30, 60, 0.70)', // 毛玻璃深蓝
    '--color-bubble-user-border': 'rgba(56, 189, 248, 0.35)', // 柔光边
    
    // 脉冲效果
    '--color-pulse-core': 'rgba(56, 189, 248, 0.4)',
    '--color-pulse-ring': '#38bdf8',
    
    // 知识图谱节点
    '--color-node-user': '#38bdf8',
    '--color-node-fact': '#818cf8',
    '--color-edge': 'rgba(56, 189, 248, 0.5)',
    
    // 发光效果 - 柔光晕
    '--color-glow': 'rgba(56, 189, 248, 0.45)', // 午夜蓝 - 柔光晕
  }
}

export const THEMES: Record<string, ThemeConfig> = { 
  polarLight, 
  cyberPurple,
  deepTechFuturistic,
  midnightBlue
}

export function buildNaiveOverrides(theme: ThemeConfig): GlobalThemeOverrides {
  const t = theme.cssVars
  return {
    common: {
      primaryColor: t['--color-primary'],
      primaryColorHover: t['--color-accent'],
      primaryColorPressed: t['--color-primary'],
      primaryColorSuppl: t['--color-primary'],
      bodyColor: t['--color-background'],
      cardColor: t['--color-surface'],
      modalColor: t['--color-surface-elevated'],
      popoverColor: t['--color-glass-bg'],
      textColor1: t['--color-text'],
      textColor2: t['--color-text-dim'],
      fontFamily: "'Inter', 'Plus Jakarta Sans', system-ui, sans-serif",
      fontFamilyMono: "'Fira Code', 'JetBrains Mono', monospace",
      borderRadius: '12px',
      borderRadiusSmall: '8px',
    },
    Layout: {
      color: 'transparent',
      headerColor: 'transparent',
      footerColor: 'transparent',
      siderColor: 'transparent',
    },
    Menu: {
      itemColorActive: 'transparent',
      itemTextColorActive: t['--color-primary'],
      itemTextColorHover: t['--color-primary'],
      itemIconColorActive: t['--color-primary'],
      itemIconColorHover: t['--color-primary'],
      fontSize: '14px',
      borderRadius: '10px',
    },
    Input: {
      borderRadius: '12px',
      color: t['--color-surface'],
      colorFocus: t['--color-surface-elevated'],
      border: `1px solid ${t['--color-outline']}`,
    },
    Button: {
      borderRadiusMedium: '10px',
      borderRadiusSmall: '8px',
      borderRadiusLarge: '12px',
    },
    Card: {
      borderRadius: '16px',
      color: t['--color-surface'],
    },
    Modal: {
      borderRadius: '16px',
      color: t['--color-surface-elevated'],
    },
    Tabs: {
      tabBorderRadius: '10px',
    },
    Tag: {
      borderRadius: '8px',
    },
    Select: {
      borderRadius: '12px',
    },
    Dropdown: {
      color: t['--color-glass-bg'],
      dividerColor: t['--color-outline'],
      borderRadius: '12px',
    },
    Popconfirm: {
      borderRadius: '12px',
    }
  }
}
