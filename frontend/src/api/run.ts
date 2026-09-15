import { apiClient } from './task'
import type { AgentRun } from '../types/run'

export async function getRun(runId: number): Promise<AgentRun> {
  const response = await apiClient.get<AgentRun>(`/api/runs/${runId}`)
  return response.data
}
