import type { Component } from "vue";

export interface ChatToolStep {
  id?: string;
  toolCallId?: string;
  toolName: string;
  arguments?: string;
  command?: string;
  input?: string;
  result?: string;
  output?: string;
  isError?: boolean;
  status?: "running" | "success" | "error";
  timestamp?: number;
}

export interface ChatMessage {
  id?: number;
  role: "user" | "assistant";
  content: string;
  timestamp: number;
  toolSteps?: ChatToolStep[];
  isToolStepsExpanded?: boolean;
}

export type ThemeKey = "polarLight" | "cyberPurple" | "deepTechFuturistic";

export interface ThemeCSSVars {
  "--color-background": string;
  "--color-surface": string;
  "--color-surface-elevated": string;
  "--color-primary": string;
  "--color-primary-dim": string;
  "--color-accent": string;
  "--color-text": string;
  "--color-text-dim": string;
  "--color-text-inverse": string;
  "--color-outline": string;
  "--color-success": string;
  "--color-warning": string;
  "--color-error": string;
  "--color-glass-bg": string;
  "--color-glass-border": string;
  "--color-bg-mesh-1": string;
  "--color-bg-mesh-2": string;
  "--color-bg-mesh-3": string;
  "--color-bg-mesh-4": string;
  "--color-bubble-ai-bg": string;
  "--color-bubble-ai-border": string;
  "--color-bubble-user-bg": string;
  "--color-bubble-user-border": string;
  "--color-pulse-core": string;
  "--color-pulse-ring": string;
  "--color-node-user": string;
  "--color-node-fact": string;
  "--color-edge": string;
  "--color-glow": string;
}

export interface ThemeConfig {
  key: ThemeKey;
  label: string;
  icon: Component;
  isDark: boolean;
  cssVars: ThemeCSSVars;
}
