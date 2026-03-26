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
    '--color-background': '#1a1625',
    '--color-surface': 'rgba(40, 30, 70, 0.75)',
    '--color-surface-elevated': 'rgba(60, 45, 100, 0.85)',
    '--color-primary': '#d8b4fe',
    '--color-primary-dim': 'rgba(216, 180, 254, 0.18)',
    '--color-accent': '#22d3ee',
    '--color-text': '#ffffff',
    '--color-text-dim': '#c7c7cc',
    '--color-text-inverse': '#1a1625',
    '--color-outline': 'rgba(216, 180, 254, 0.25)',
    '--color-success': '#4ade80',
    '--color-warning': '#fbbf24',
    '--color-error': '#f87171',
    '--color-glass-bg': 'rgba(30, 20, 60, 0.85)',
    '--color-glass-border': 'rgba(216, 180, 254, 0.2)',
    '--color-bg-mesh-1': '#1a1625',
    '--color-bg-mesh-2': '#4c1d95',
    '--color-bg-mesh-3': '#2e1065',
    '--color-bg-mesh-4': '#1a1625',
    '--color-bubble-ai-bg': 'rgba(216, 180, 254, 0.12)',
    '--color-bubble-ai-border': 'rgba(216, 180, 254, 0.3)',
    '--color-bubble-user-bg': 'rgba(216, 180, 254, 0.18)',
    '--color-bubble-user-border': 'rgba(216, 180, 254, 0.4)',
    '--color-pulse-core': 'rgba(216, 180, 254, 0.3)',
    '--color-pulse-ring': '#d8b4fe',
    '--color-node-user': '#d8b4fe',
    '--color-node-fact': '#22d3ee',
    '--color-edge': 'rgba(216, 180, 254, 0.5)',
    '--color-glow': 'rgba(216, 180, 254, 0.4)',
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
