import { apiClient } from './task'
import type { AgentRun } from '../types/run'
import type { AgentStep } from '../types/step'

export async function getRun(runId: number): Promise<AgentRun> {
  const response = await apiClient.get<AgentRun>(`/api/runs/${runId}`)
  return response.data
}

export async function getRunSteps(runId: number): Promise<AgentStep[]> {
  const response = await apiClient.get<AgentStep[]>(`/api/runs/${runId}/steps`)
  return response.data
}
