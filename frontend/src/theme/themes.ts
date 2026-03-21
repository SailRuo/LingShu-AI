import type { GlobalThemeOverrides } from 'naive-ui'
import type { ThemeConfig } from '@/types'
import { Sun, Sparkles, Cpu } from 'lucide-vue-next'

export const polarLight: ThemeConfig = {
  key: 'polarLight',
  label: '极地白',
  icon: Sun,
  isDark: false,
  cssVars: {
    '--color-background': '#f8fafc',
    '--color-surface': 'rgba(255, 255, 255, 0.8)',
    '--color-surface-elevated': 'rgba(255, 255, 255, 0.95)',
    '--color-primary': '#0f766e',
    '--color-primary-dim': 'rgba(15, 118, 110, 0.1)',
    '--color-accent': '#ea580c',
    '--color-text': '#0f172a',
    '--color-text-dim': '#64748b',
    '--color-text-inverse': '#ffffff',
    '--color-outline': 'rgba(0, 0, 0, 0.06)',
    '--color-success': '#059669',
    '--color-warning': '#d97706',
    '--color-error': '#dc2626',
    '--color-glass-bg': 'rgba(255, 255, 255, 0.8)',
    '--color-glass-border': 'rgba(0, 0, 0, 0.06)',
    '--color-bg-mesh-1': '#f8fafc',
    '--color-bg-mesh-2': '#e2e8f0',
    '--color-bg-mesh-3': '#ccfbf1',
    '--color-bg-mesh-4': '#f8fafc',
    '--color-bubble-ai-bg': 'rgba(15, 118, 110, 0.06)',
    '--color-bubble-ai-border': 'rgba(15, 118, 110, 0.1)',
    '--color-bubble-user-bg': 'rgba(15, 118, 110, 0.08)',
    '--color-bubble-user-border': 'rgba(15, 118, 110, 0.15)',
    '--color-pulse-core': 'rgba(15, 118, 110, 0.15)',
    '--color-pulse-ring': '#0f766e',
    '--color-node-user': '#0f766e',
    '--color-node-fact': '#ea580c',
    '--color-edge': 'rgba(15, 118, 110, 0.3)',
    '--color-glow': 'rgba(15, 118, 110, 0.2)',
  }
}

export const cyberPurple: ThemeConfig = {
  key: 'cyberPurple',
  label: '赛博紫',
  icon: Sparkles,
  isDark: true,
  cssVars: {
    '--color-background': '#0c0a1a',
    '--color-surface': 'rgba(30, 20, 50, 0.6)',
    '--color-surface-elevated': 'rgba(50, 30, 80, 0.7)',
    '--color-primary': '#c084fc',
    '--color-primary-dim': 'rgba(192, 132, 252, 0.15)',
    '--color-accent': '#22d3ee',
    '--color-text': '#faf5ff',
    '--color-text-dim': '#a1a1aa',
    '--color-text-inverse': '#0c0a1a',
    '--color-outline': 'rgba(192, 132, 252, 0.12)',
    '--color-success': '#4ade80',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    '--color-glass-bg': 'rgba(20, 10, 40, 0.8)',
    '--color-glass-border': 'rgba(192, 132, 252, 0.1)',
    '--color-bg-mesh-1': '#0c0a1a',
    '--color-bg-mesh-2': '#2e1065',
    '--color-bg-mesh-3': '#1e1b4b',
    '--color-bg-mesh-4': '#0c0a1a',
    '--color-bubble-ai-bg': 'rgba(192, 132, 252, 0.06)',
    '--color-bubble-ai-border': 'rgba(192, 132, 252, 0.12)',
    '--color-bubble-user-bg': 'rgba(192, 132, 252, 0.1)',
    '--color-bubble-user-border': 'rgba(192, 132, 252, 0.18)',
    '--color-pulse-core': 'rgba(192, 132, 252, 0.2)',
    '--color-pulse-ring': '#c084fc',
    '--color-node-user': '#c084fc',
    '--color-node-fact': '#22d3ee',
    '--color-edge': 'rgba(192, 132, 252, 0.4)',
    '--color-glow': 'rgba(192, 132, 252, 0.3)',
  }
}

export const deepTechFuturistic: ThemeConfig = {
  key: 'deepTechFuturistic',
  label: '深色·科技感',
  icon: Cpu,
  isDark: true,
  cssVars: {
    '--color-background': '#050505',
    '--color-surface': 'rgba(10, 15, 20, 0.8)',
    '--color-surface-elevated': 'rgba(15, 25, 35, 0.9)',
    '--color-primary': '#00ff88',
    '--color-primary-dim': 'rgba(0, 255, 136, 0.1)',
    '--color-accent': '#00d4ff',
    '--color-text': '#e0e0e0',
    '--color-text-dim': '#808080',
    '--color-text-inverse': '#050505',
    '--color-outline': 'rgba(0, 255, 136, 0.2)',
    '--color-success': '#00ff88',
    '--color-warning': '#ffcc00',
    '--color-error': '#ff3366',
    '--color-glass-bg': 'rgba(5, 10, 15, 0.85)',
    '--color-glass-border': 'rgba(0, 255, 136, 0.15)',
    '--color-bg-mesh-1': '#050505',
    '--color-bg-mesh-2': '#0a0f0a',
    '--color-bg-mesh-3': '#001a0d',
    '--color-bg-mesh-4': '#050505',
    '--color-bubble-ai-bg': 'rgba(0, 255, 136, 0.04)',
    '--color-bubble-ai-border': 'rgba(0, 255, 136, 0.12)',
    '--color-bubble-user-bg': 'rgba(0, 255, 136, 0.06)',
    '--color-bubble-user-border': 'rgba(0, 255, 136, 0.18)',
    '--color-pulse-core': 'rgba(0, 255, 136, 0.25)',
    '--color-pulse-ring': '#00ff88',
    '--color-node-user': '#00ff88',
    '--color-node-fact': '#00d4ff',
    '--color-edge': 'rgba(0, 255, 136, 0.5)',
    '--color-glow': 'rgba(0, 255, 136, 0.4)',
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
