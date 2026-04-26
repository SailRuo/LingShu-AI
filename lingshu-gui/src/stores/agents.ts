import { defineStore } from 'pinia';
import { ref } from 'vue';

export interface AgentConfig {
  id?: number;
  name: string;
  displayName: string;
  systemPrompt: string;
  factExtractionPrompt?: string;
  behaviorPrinciples?: string;
  decisionMechanism?: string;
  toolCallRules?: string;
  emotionalStrategy?: string;
  greetingTriggers?: string;
  hiddenRules?: string;
  avatar?: string;
  color?: string;
  isDefault: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

function getFullUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseUrl}${path}`;
}

export const useAgentsStore = defineStore('agents', () => {
  const agents = ref<AgentConfig[]>([]);
  const isLoading = ref(false);
  const isLoaded = ref(false);

  async function fetchAgents(force = false) {
    if (isLoaded.value && !force) return;
    if (isLoading.value) return;

    isLoading.value = true;
    try {
      const res = await fetch(getFullUrl('/api/agents'));
      if (res.ok) {
        agents.value = await res.json();
        isLoaded.value = true;
      }
    } catch (err) {
      console.error('Failed to fetch agents', err);
    } finally {
      isLoading.value = false;
    }
  }

  async function createAgent(agentData: Partial<AgentConfig>) {
    try {
      const res = await fetch(getFullUrl('/api/agents'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agentData),
      });
      if (res.ok) {
        await fetchAgents(true);
        return await res.json();
      }
    } catch (err) {
      console.error('Failed to create agent', err);
    }
    return null;
  }

  async function updateAgent(id: number, agentData: Partial<AgentConfig>) {
    try {
      const res = await fetch(getFullUrl(`/api/agents/${id}`), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agentData),
      });
      if (res.ok) {
        await fetchAgents(true);
        return await res.json();
      }
    } catch (err) {
      console.error('Failed to update agent', err);
    }
    return null;
  }

  async function deleteAgent(id: number) {
    try {
      const res = await fetch(getFullUrl(`/api/agents/${id}`), {
        method: 'DELETE',
      });
      if (res.ok) {
        await fetchAgents(true);
        return true;
      }
    } catch (err) {
      console.error('Failed to delete agent', err);
    }
    return false;
  }

  async function setDefaultAgent(id: number) {
    try {
      const res = await fetch(getFullUrl(`/api/agents/${id}/set-default`), {
        method: 'POST',
      });
      if (res.ok) {
        await fetchAgents(true);
        return true;
      }
    } catch (err) {
      console.error('Failed to set default agent', err);
    }
    return false;
  }

  async function getAgentDefaults() {
    try {
      const res = await fetch(getFullUrl('/api/agents/defaults'));
      if (res.ok) {
        return await res.json();
      }
    } catch (err) {
      console.error('Failed to get agent defaults', err);
    }
    return null;
  }

  return {
    agents,
    isLoading,
    isLoaded,
    fetchAgents,
    createAgent,
    updateAgent,
    deleteAgent,
    setDefaultAgent,
    getAgentDefaults,
  };
});
