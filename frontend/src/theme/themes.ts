import type { GlobalThemeOverrides } from 'naive-ui'
import type { ThemeConfig } from '@/types'
import { Sun, Sparkles, Cpu } from 'lucide-vue-next'

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
    '--color-glass-bg': 'rgba(255, 255, 255, 0.95)',
    '--color-glass-border': 'rgba(0, 0, 0, 0.12)',
    '--color-bg-mesh-1': '#f1f5f9',
    '--color-bg-mesh-2': '#cbd5e1',
    '--color-bg-mesh-3': '#99f6e4',
    '--color-bg-mesh-4': '#f1f5f9',
    '--color-bubble-ai-bg': 'rgba(13, 148, 136, 0.1)',
    '--color-bubble-ai-border': 'rgba(13, 148, 136, 0.2)',
    '--color-bubble-user-bg': 'rgba(13, 148, 136, 0.15)',
    '--color-bubble-user-border': 'rgba(13, 148, 136, 0.25)',
    '--color-pulse-core': 'rgba(13, 148, 136, 0.2)',
    '--color-pulse-ring': '#0d9488',
    '--color-node-user': '#0d9488',
    '--color-node-fact': '#c2410c',
    '--color-edge': 'rgba(13, 148, 136, 0.4)',
    '--color-glow': 'rgba(13, 148, 136, 0.3)',
  }
}

export const cyberPurple: ThemeConfig = {
  key: 'cyberPurple',
  label: '赛博紫',
  icon: Sparkles,
  isDark: true,
  cssVars: {
    // 核心颜色 - 提高对比度和霓虹感
    '--color-background': '#0f0a1e', // 更深的蓝紫黑背景
    '--color-surface': 'rgba(35, 25, 65, 0.85)', // 加深表面色
    '--color-surface-elevated': 'rgba(55, 40, 95, 0.92)', //  elevated 层更亮
    '--color-primary': '#f0abfc', // 霓虹紫 - 更高亮度
    '--color-primary-dim': 'rgba(240, 171, 252, 0.2)', // 增强发光底衬
    '--color-accent': '#22d3ee', // 保持青蓝色作为互补色
    
    // 文字系统 - 优化可读性
    '--color-text': '#ffffff',
    '--color-text-dim': '#a5a5b5', // 降低次要文字明度，增强层次
    '--color-text-inverse': '#0f0a1e',
    
    // 边框和轮廓 - 增强可见度
    '--color-outline': 'rgba(240, 171, 252, 0.35)', // 更高不透明度
    
    // 状态色 - 保持高可见度
    '--color-success': '#4ade80',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    
    // 玻璃态效果 - 增强质感
    '--color-glass-bg': 'rgba(25, 15, 55, 0.88)', // 更深的玻璃背景
    '--color-glass-border': 'rgba(240, 171, 252, 0.3)', // 更强的边框
    
    // Mesh 渐变背景 - 强化紫色调
    '--color-bg-mesh-1': '#0f0a1e',
    '--color-bg-mesh-2': '#5b21b6', // 深紫色
    '--color-bg-mesh-3': '#3b0764', // 暗紫色
    '--color-bg-mesh-4': '#0f0a1e',
    
    // 气泡组件 - 增强对比
    '--color-bubble-ai-bg': 'rgba(240, 171, 252, 0.15)', // 更高的不透明度
    '--color-bubble-ai-border': 'rgba(240, 171, 252, 0.45)',
    '--color-bubble-user-bg': 'rgba(240, 171, 252, 0.25)',
    '--color-bubble-user-border': 'rgba(240, 171, 252, 0.55)',
    
    // 脉冲效果 - 更强光晕
    '--color-pulse-core': 'rgba(240, 171, 252, 0.45)',
    '--color-pulse-ring': '#f0abfc',
    
    // 知识图谱节点 - 高对比度
    '--color-node-user': '#f0abfc',
    '--color-node-fact': '#22d3ee',
    '--color-edge': 'rgba(240, 171, 252, 0.65)', // 更强的边线
    
    // 发光效果 - 霓虹感
    '--color-glow': 'rgba(240, 171, 252, 0.6)', // 增强光晕
  }
}

export const deepTechFuturistic: ThemeConfig = {
  key: 'deepTechFuturistic',
  label: '深色·科技感',
  icon: Cpu,
  isDark: true,
  cssVars: {
    '--color-background': '#0a0e1a',
    '--color-surface': 'rgba(15, 25, 40, 0.85)',
    '--color-surface-elevated': 'rgba(20, 35, 55, 0.9)',
    '--color-primary': '#00ff88',
    '--color-primary-dim': 'rgba(0, 255, 136, 0.12)',
    '--color-accent': '#00d4ff',
    '--color-text': '#f0f0f0',
    '--color-text-dim': '#999999',
    '--color-text-inverse': '#0a0e1a',
    '--color-outline': 'rgba(0, 255, 136, 0.25)',
    '--color-success': '#00ff88',
    '--color-warning': '#ffcc00',
    '--color-error': '#ff3366',
    '--color-glass-bg': 'rgba(10, 20, 30, 0.9)',
    '--color-glass-border': 'rgba(0, 255, 136, 0.2)',
    '--color-bg-mesh-1': '#0a0e1a',
    '--color-bg-mesh-2': '#0f1a2e',
    '--color-bg-mesh-3': '#001a0d',
    '--color-bg-mesh-4': '#0a0e1a',
    '--color-bubble-ai-bg': 'rgba(0, 255, 136, 0.06)',
    '--color-bubble-ai-border': 'rgba(0, 255, 136, 0.18)',
    '--color-bubble-user-bg': 'rgba(0, 255, 136, 0.1)',
    '--color-bubble-user-border': 'rgba(0, 255, 136, 0.25)',
    '--color-pulse-core': 'rgba(0, 255, 136, 0.3)',
    '--color-pulse-ring': '#00ff88',
    '--color-node-user': '#00ff88',
    '--color-node-fact': '#00d4ff',
    '--color-edge': 'rgba(0, 255, 136, 0.5)',
    '--color-glow': 'rgba(0, 255, 136, 0.45)',
  }
}

export const THEMES: Record<string, ThemeConfig> = { 
  polarLight, 
  cyberPurple,
  deepTechFuturistic
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
