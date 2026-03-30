export function getApiBaseUrl(): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  return baseUrl
}

export function getFullUrl(path: string): string {
  const baseUrl = getApiBaseUrl()
  // If path starts with /api, we might want to keep it or remove it depending on backend
  // In this project, the backend seems to expect /api prefix (based on Vite proxy)
  return `${baseUrl}${path}`
}
