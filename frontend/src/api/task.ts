import axios from 'axios'
import type { AgentTask } from '../types/task'
import type { AgentRun } from '../types/run'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
})

export async function getTasks(): Promise<AgentTask[]> {
  const response = await apiClient.get<AgentTask[]>('/api/tasks')
  return response.data
}

export async function createTask(title: string): Promise<AgentTask> {
  const response = await apiClient.post<AgentTask>('/api/tasks', { title })
  return response.data
}

export async function runTask(id: number): Promise<AgentRun> {
  const response = await apiClient.post<AgentRun>(`/api/tasks/${id}/run`)
  return response.data
}
