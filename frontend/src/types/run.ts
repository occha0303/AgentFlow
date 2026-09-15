export type AgentRunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AgentRun {
  runId: number
  taskId: number
  status: AgentRunStatus
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}
