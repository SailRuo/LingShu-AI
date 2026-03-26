import { ref } from 'vue'

export interface Agent {
  id: number
  name: string
  displayName: string
  systemPrompt: string
  factExtractionPrompt: string
  behaviorPrinciples: string
  decisionMechanism: string
  toolCallRules: string
  emotionalStrategy: string
  greetingTriggers: string
  hiddenRules: string
  avatar: string
  color: string
  isDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

const agents = ref<Agent[]>([])
const isLoaded = ref(false)
const isLoading = ref(false)

async function fetchAgents(force = false) {
  if (isLoaded.value && !force) return
  if (isLoading.value) return
  
  isLoading.value = true
  try {
    const res = await fetch('/api/agents')
    if (res.ok) {
      agents.value = await res.json()
      isLoaded.value = true
    }
  } catch (err) {
    console.error('Failed to fetch agents', err)
  } finally {
    isLoading.value = false
  }
}

async function createAgent(agentData: Partial<Agent>) {
  const res = await fetch('/api/agents', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(agentData)
  })
  if (res.ok) {
    await fetchAgents(true)
  }
  return res
}

async function updateAgent(id: number, agentData: Partial<Agent>) {
  const res = await fetch(`/api/agents/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(agentData)
  })
  if (res.ok) {
    await fetchAgents(true)
  }
  return res
}

async function deleteAgent(id: number) {
  const res = await fetch(`/api/agents/${id}`, { method: 'DELETE' })
  if (res.ok) {
    await fetchAgents(true)
  }
  return res
}

async function setDefaultAgent(id: number) {
  const res = await fetch(`/api/agents/${id}/set-default`, { method: 'POST' })
  if (res.ok) {
    await fetchAgents(true)
  }
  return res
}

async function getAgentDefaults() {
  const res = await fetch('/api/agents/defaults')
  return res
}

export function useAgents() {
  return {
    agents,
    isLoaded,
    isLoading,
    fetchAgents,
    createAgent,
    updateAgent,
    deleteAgent,
    setDefaultAgent,
    getAgentDefaults
  }
}
