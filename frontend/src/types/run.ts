export type AgentRunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AgentRun {
  runId: number
  taskId: number
  status: AgentRunStatus
  resultText: string | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}
