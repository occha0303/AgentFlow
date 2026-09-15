export type AgentStepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AgentStep {
  id: number
  runId: number
  stepOrder: number
  stepType: string
  status: AgentStepStatus
  inputSummary: string | null
  outputSummary: string | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}
